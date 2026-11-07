package com.accesmed.backend.Application;

import com.accesmed.backend.Records.Chatwoot.Request.ReceiveMensajeChatwootRequest;
import com.accesmed.backend.Records.Chatwoot.Request.RemitenteChatwootRequest;
import com.accesmed.backend.Services.Clients.ChatwootClient;
import com.accesmed.backend.Services.Clients.FlowiseClient;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Caso de uso del pasamanos Chatwoot, Flowise y Chatwoot: recibe el webhook del Agent Bot,
 * verifica su firma, decide si el mensaje le corresponde al bot y, en segundo plano, consulta a
 * Flowise y devuelve la respuesta por Chatwoot (o deriva la conversación a una persona). No toca
 * el dominio: solo usa los clientes HTTP de {@code Services/Clients}.
 */
@Slf4j
@Service
public class ChatwootApp {

    //region ========== Constantes ==========

    /**
     * Token que el agente agrega a su respuesta cuando el paciente pide una persona (ECU-TURN-23).
     */
    static final String TOKEN_HANDOFF = "—HANDOFF—";

    /**
     * Mensaje al paciente cuando el agente no pudo responder.
     */
    static final String MENSAJE_ERROR_TECNICO = "Estamos con problemas técnicos, en breve te atiende una persona.";

    private static final long TOLERANCIA_TIMESTAMP_SEGUNDOS = 300;
    private static final String PREFIJO_FIRMA = "sha256=";

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final ObjectMapper objectMapper;
    private final ChatwootClient chatwootClient;
    private final FlowiseClient flowiseClient;
    private final Executor chatwootTaskExecutor;
    private final String webhookSecret;

    //endregion

    /**
     * Construye el caso de uso con sus clientes y el executor del procesamiento asíncrono.
     *
     * @param objectMapper {@code ObjectMapper} parser del payload del webhook
     * @param chatwootClient {@code ChatwootClient} cliente de la API de Chatwoot
     * @param flowiseClient {@code FlowiseClient} cliente de Flowise
     * @param chatwootTaskExecutor {@code Executor} hilos del procesamiento asíncrono
     * @param webhookSecret {@code String} secret del webhook ({@code accesmed.chatwoot.webhook-secret}); vacío no verifica
     */
    public ChatwootApp(ObjectMapper objectMapper, ChatwootClient chatwootClient, FlowiseClient flowiseClient,
            @Qualifier("chatwootTaskExecutor") Executor chatwootTaskExecutor,
            @Value("${accesmed.chatwoot.webhook-secret:}") String webhookSecret) {

        this.objectMapper = objectMapper;
        this.chatwootClient = chatwootClient;
        this.flowiseClient = flowiseClient;
        this.chatwootTaskExecutor = chatwootTaskExecutor;
        this.webhookSecret = webhookSecret;

    }

    //region ========== Métodos ==========

    /**
     * Recibe un evento del webhook de Chatwoot: verifica la firma, lo parsea y, si es un mensaje
     * entrante del paciente en una conversación manejada por el bot, encola su procesamiento
     * asíncrono. Cualquier otro evento se ignora.
     *
     * @param cuerpoCrudo {@code String} body del request sin parsear (sobre él se calcula la firma)
     * @param firma {@code String} header {@code X-Chatwoot-Signature} ({@code sha256=<hex>}), o {@code null}
     * @param timestamp {@code String} header {@code X-Chatwoot-Timestamp} (epoch en segundos), o {@code null}
     * @throws ReglaNegocioException {@code ReglaNegocioException} (código {@code FIRMA_WEBHOOK_INVALIDA}, HTTP 409) si la firma falta, es inválida o el
     *         timestamp está vencido
     * @throws ValidacionException {@code ValidacionException} si el body no es un JSON válido
     */
    public void receiveMensajeChatwoot(String cuerpoCrudo, String firma, String timestamp) {

        //Verificar que el request venga de Chatwoot
        validateFirmaWebhook(cuerpoCrudo, firma, timestamp);

        //Parsear el payload (los campos desconocidos se ignoran)
        ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest = parseMensajeChatwoot(cuerpoCrudo);

        //Procesar solo los mensajes entrantes del paciente en conversaciones del bot
        if (!esMensajeParaElBot(receiveMensajeChatwootRequest)) {
            log.debug("Evento de Chatwoot ignorado: event={}, messageType={}",
                    receiveMensajeChatwootRequest.event(), receiveMensajeChatwootRequest.messageType());
            return;
        }

        long conversationId = receiveMensajeChatwootRequest.conversation().id();
        log.info("Mensaje de Chatwoot recibido: conversación={}", conversationId);

        //El teléfono verificado del canal: sender y, de respaldo, el contacto de la conversación
        String numeroTelefono = findNumeroTelefono(receiveMensajeChatwootRequest);
        if (numeroTelefono == null || numeroTelefono.isBlank()) {
            log.warn("El mensaje de la conversación {} no trae el teléfono del contacto: se deriva a una persona",
                    conversationId);
            chatwootTaskExecutor.execute(() -> derivarAPersona(conversationId, MENSAJE_ERROR_TECNICO));
            return;
        }

        //Responder en segundo plano: el webhook no puede esperar al LLM
        String contenido = receiveMensajeChatwootRequest.content();
        chatwootTaskExecutor.execute(() -> processMensajeChatwoot(conversationId, contenido, numeroTelefono));

    }

    /**
     * Consulta a Flowise y devuelve la respuesta por Chatwoot. Si la respuesta trae el token
     * {@code —HANDOFF—}, lo quita, envía el texto restante y deriva la conversación a una persona.
     * Si Flowise falla, avisa al paciente y deriva. Si Chatwoot falla, solo se loguea (no hay a
     * quién responder).
     *
     * @param conversationId {@code long} identificador de la conversación de Chatwoot
     * @param contenido {@code String} mensaje del paciente
     * @param numeroTelefono {@code String} teléfono verificado del paciente
     */
    void processMensajeChatwoot(long conversationId, String contenido, String numeroTelefono) {

        String respuestaFlowise;
        try {
            respuestaFlowise = flowiseClient.sendPreguntaFlowise(contenido, "chatwoot-" + conversationId, numeroTelefono);
        } catch (RuntimeException excepcion) {
            log.error("Flowise falló al responder la conversación {}", conversationId, excepcion);
            derivarAPersona(conversationId, MENSAJE_ERROR_TECNICO);
            return;
        }

        if (respuestaFlowise == null || respuestaFlowise.isBlank()) {
            log.error("Flowise devolvió una respuesta vacía para la conversación {}", conversationId);
            derivarAPersona(conversationId, MENSAJE_ERROR_TECNICO);
            return;
        }

        boolean pideHandoff = respuestaFlowise.contains(TOKEN_HANDOFF);
        String textoRespuesta = respuestaFlowise.replace(TOKEN_HANDOFF, "").trim();

        try {
            if (!textoRespuesta.isEmpty()) {
                chatwootClient.sendMensajeChatwoot(conversationId, textoRespuesta);
            }
            if (pideHandoff) {
                log.info("Handoff a una persona solicitado: conversación={}", conversationId);
                chatwootClient.openConversacionChatwoot(conversationId);
            }
        } catch (RuntimeException excepcion) {
            log.error("Chatwoot falló al responder la conversación {}", conversationId, excepcion);
        }

    }

    //endregion

    //region ========== Helpers privados ==========

    /**
     * Verifica la firma HMAC del webhook: {@code sha256=} más el hex de HMAC-SHA256 del secret sobre
     * {@code "<timestamp>.<cuerpo>"}, comparada en tiempo constante, con timestamp de hasta 5 minutos.
     * Si no hay secret configurado, no verifica y deja un {@code warn} (solo para probar la integración
     * cuando el Agent Bot no firma).
     *
     * @param cuerpoCrudo {@code String} body del request sin parsear
     * @param firma {@code String} header de firma, o {@code null}
     * @param timestamp {@code String} header de timestamp, o {@code null}
     * @throws ReglaNegocioException {@code ReglaNegocioException} (código {@code FIRMA_WEBHOOK_INVALIDA}, HTTP 409) si la firma falta, es inválida o el
     *         timestamp está vencido o no es numérico
     */
    private void validateFirmaWebhook(String cuerpoCrudo, String firma, String timestamp) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("accesmed.chatwoot.webhook-secret está vacío: el webhook de Chatwoot se acepta SIN verificar la firma");
            return;
        }

        if (firma == null || timestamp == null) {
            log.warn("Webhook de Chatwoot rechazado: faltan los headers de firma");
            throw new ReglaNegocioException(getClass(), "FIRMA_WEBHOOK_INVALIDA", "Firma del webhook ausente.");
        }

        long timestampSegundos;
        try {
            timestampSegundos = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException excepcion) {
            log.warn("Webhook de Chatwoot rechazado: timestamp no numérico");
            throw new ReglaNegocioException(getClass(), "FIRMA_WEBHOOK_INVALIDA", "Timestamp del webhook inválido.");
        }

        if (Math.abs(Instant.now().getEpochSecond() - timestampSegundos) > TOLERANCIA_TIMESTAMP_SEGUNDOS) {
            log.warn("Webhook de Chatwoot rechazado: timestamp fuera de la ventana de {} s", TOLERANCIA_TIMESTAMP_SEGUNDOS);
            throw new ReglaNegocioException(getClass(), "FIRMA_WEBHOOK_INVALIDA", "Timestamp del webhook vencido.");
        }

        String firmaEsperada = PREFIJO_FIRMA + calculateHmacHex(timestamp.trim() + "." + cuerpoCrudo);
        boolean firmaValida = MessageDigest.isEqual(
                firmaEsperada.getBytes(StandardCharsets.UTF_8), firma.trim().getBytes(StandardCharsets.UTF_8));
        if (!firmaValida) {
            log.warn("Webhook de Chatwoot rechazado: la firma no coincide");
            throw new ReglaNegocioException(getClass(), "FIRMA_WEBHOOK_INVALIDA", "Firma del webhook inválida.");
        }

    }

    /**
     * Calcula HMAC-SHA256 del texto con el secret del webhook.
     *
     * @param texto {@code String} texto a firmar
     * @return {@code String} la firma en hexadecimal minúscula
     */
    private String calculateHmacHex(String texto) {

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException("HmacSHA256 no está disponible en esta JVM", excepcion);
        }

    }

    /**
     * Parsea el body crudo del webhook.
     *
     * @param cuerpoCrudo {@code String} body del request
     * @return {@code ReceiveMensajeChatwootRequest} el payload parseado
     * @throws ValidacionException {@code ValidacionException} si el body no es un JSON válido
     */
    private ReceiveMensajeChatwootRequest parseMensajeChatwoot(String cuerpoCrudo) {

        try {
            return objectMapper.readValue(cuerpoCrudo, ReceiveMensajeChatwootRequest.class);
        } catch (JacksonException excepcion) {
            log.warn("El payload del webhook de Chatwoot no es un JSON válido: {}", excepcion.getOriginalMessage());
            throw new ValidacionException(getClass(), List.of("El cuerpo del webhook no es un JSON válido."));
        }

    }

    /**
     * Indica si el evento es un mensaje entrante y público del paciente en una conversación
     * pendiente (manejada por el bot) y con texto.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code boolean} {@code true} si el bot debe responderlo
     */
    private boolean esMensajeParaElBot(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        boolean esEntrante = "incoming".equals(receiveMensajeChatwootRequest.messageType())
                || "0".equals(receiveMensajeChatwootRequest.messageType());

        return "message_created".equals(receiveMensajeChatwootRequest.event())
                && esEntrante
                && !Boolean.TRUE.equals(receiveMensajeChatwootRequest.privado())
                && receiveMensajeChatwootRequest.conversation() != null
                && receiveMensajeChatwootRequest.conversation().id() != null
                && "pending".equals(receiveMensajeChatwootRequest.conversation().status())
                && receiveMensajeChatwootRequest.content() != null
                && !receiveMensajeChatwootRequest.content().isBlank();

    }

    /**
     * Obtiene el teléfono del contacto: el del {@code sender} y, si no lo trae, el del contacto de
     * la conversación.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code String} el teléfono, o {@code null} si el payload no lo trae
     */
    private String findNumeroTelefono(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        RemitenteChatwootRequest sender = receiveMensajeChatwootRequest.sender();
        if (sender != null && sender.phoneNumber() != null && !sender.phoneNumber().isBlank()) {
            return sender.phoneNumber();
        }

        var meta = receiveMensajeChatwootRequest.conversation().meta();
        if (meta != null && meta.sender() != null) {
            return meta.sender().phoneNumber();
        }

        return null;

    }

    /**
     * Avisa al paciente y pasa la conversación a una persona. Si Chatwoot falla, solo se loguea.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param mensaje {@code String} texto para el paciente
     */
    private void derivarAPersona(long conversationId, String mensaje) {

        try {
            chatwootClient.sendMensajeChatwoot(conversationId, mensaje);
            chatwootClient.openConversacionChatwoot(conversationId);
        } catch (RuntimeException excepcion) {
            log.error("Chatwoot falló al derivar la conversación {} a una persona", conversationId, excepcion);
        }

    }

    //endregion

}
