package com.accesmed.backend.Application;

import com.accesmed.backend.Records.Chatwoot.Request.ReceiveMensajeChatwootRequest;
import com.accesmed.backend.Records.Chatwoot.Request.RemitenteChatwootRequest;
import com.accesmed.backend.Services.Clients.ChatwootClient;
import com.accesmed.backend.Services.Clients.FlowiseClient;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Utils.AgrupadorMensajes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Caso de uso del pasamanos Chatwoot, Flowise y Chatwoot (contrato en
 * {@code Docs/Features/Chatbot.md §6}): recibe el webhook del Agent Bot (la firma ya la verificó
 * {@code ChatwootFirmaFilter}), decide si el mensaje le corresponde al bot, agrupa los mensajes en
 * ráfaga por conversación y, en segundo plano, consulta a Flowise y devuelve la respuesta por
 * Chatwoot (o deriva la conversación a una persona). No toca el dominio: solo usa los clientes HTTP
 * de {@code Services/Clients}.
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

    /**
     * Mensaje al paciente cuando manda un audio, imagen o sticker (sin texto).
     */
    static final String MENSAJE_SOLO_TEXTO = "Por ahora solo puedo leer mensajes de texto. ¿Me lo escribís?";

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final ObjectMapper objectMapper;
    private final ChatwootClient chatwootClient;
    private final FlowiseClient flowiseClient;
    private final Executor chatwootTaskExecutor;
    private final AgrupadorMensajes<ReceiveMensajeChatwootRequest> agrupadorMensajesChatwoot;

    //endregion

    /**
     * Construye el caso de uso con sus clientes, el executor y el agrupador de mensajes.
     *
     * @param objectMapper {@code ObjectMapper} parser del payload del webhook
     * @param chatwootClient {@code ChatwootClient} cliente de la API de Chatwoot
     * @param flowiseClient {@code FlowiseClient} cliente de Flowise
     * @param chatwootTaskExecutor {@code Executor} hilos de las respuestas que no pasan por el agrupador
     * @param agrupadorMensajesChatwoot {@code AgrupadorMensajes<ReceiveMensajeChatwootRequest>} agrupador por conversación
     */
    public ChatwootApp(ObjectMapper objectMapper, ChatwootClient chatwootClient, FlowiseClient flowiseClient,
            @Qualifier("chatwootTaskExecutor") Executor chatwootTaskExecutor,
            AgrupadorMensajes<ReceiveMensajeChatwootRequest> agrupadorMensajesChatwoot) {

        this.objectMapper = objectMapper;
        this.chatwootClient = chatwootClient;
        this.flowiseClient = flowiseClient;
        this.chatwootTaskExecutor = chatwootTaskExecutor;
        this.agrupadorMensajesChatwoot = agrupadorMensajesChatwoot;

    }

    //region ========== Métodos ==========

    /**
     * Recibe un evento del webhook de Chatwoot. Si es un mensaje entrante del paciente en una
     * conversación del bot, lo suma a la tanda de su conversación; si no trae texto (audio, imagen)
     * avisa que solo se leen textos; si no trae teléfono deriva a una persona. Cualquier otro
     * evento se ignora.
     *
     * @param cuerpoCrudo {@code String} body del request sin parsear
     * @param idEntrega {@code String} header {@code X-Chatwoot-Delivery} (solo para el log), o {@code null}
     * @throws ValidacionException {@code ValidacionException} si el body no es un JSON válido
     */
    public void receiveMensajeChatwoot(String cuerpoCrudo, String idEntrega) {

        //Parsear el payload (los campos desconocidos se ignoran)
        ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest = parseMensajeChatwoot(cuerpoCrudo);

        //Procesar solo los mensajes entrantes del paciente en conversaciones del bot
        if (!esMensajeParaElBot(receiveMensajeChatwootRequest)) {
            log.debug("Evento de Chatwoot ignorado: event={}, messageType={}, entrega={}",
                    receiveMensajeChatwootRequest.event(), receiveMensajeChatwootRequest.messageType(), idEntrega);
            return;
        }

        long conversationId = receiveMensajeChatwootRequest.conversation().id();
        log.info("Mensaje de Chatwoot recibido: conversación={}, mensaje={}, entrega={}",
                conversationId, receiveMensajeChatwootRequest.id(), idEntrega);

        //El teléfono verificado del canal: sin él no se puede identificar al paciente
        String numeroTelefono = findNumeroTelefono(receiveMensajeChatwootRequest);
        if (numeroTelefono == null || numeroTelefono.isBlank()) {
            log.warn("El mensaje de la conversación {} no trae el teléfono del contacto: se deriva a una persona",
                    conversationId);
            chatwootTaskExecutor.execute(() -> derivarAPersona(conversationId, MENSAJE_ERROR_TECNICO));
            return;
        }

        //Audio, imagen o sticker: el agente solo entiende texto
        if (receiveMensajeChatwootRequest.content() == null || receiveMensajeChatwootRequest.content().isBlank()) {
            log.info("Mensaje sin texto en la conversación {}: se pide que lo escriba", conversationId);
            chatwootTaskExecutor.execute(() -> sendMensajeSeguro(conversationId, MENSAJE_SOLO_TEXTO));
            return;
        }

        //Agrupar la ráfaga: la tanda se procesa en segundo plano (el webhook no espera al LLM)
        agrupadorMensajesChatwoot.addElemento(conversationId, receiveMensajeChatwootRequest, this::processTandaChatwoot);

    }

    /**
     * Procesa una tanda de mensajes de una conversación: los une en una sola pregunta, consulta a
     * Flowise y devuelve la respuesta por Chatwoot. Si la respuesta trae el token
     * {@code —HANDOFF—}, lo quita, envía el texto restante y deriva la conversación a una persona.
     * Si Flowise falla, avisa al paciente y deriva. Si Chatwoot falla, solo se loguea (no hay a
     * quién responder).
     *
     * @param tanda {@code List<ReceiveMensajeChatwootRequest>} mensajes de la misma conversación, en orden de llegada
     */
    void processTandaChatwoot(List<ReceiveMensajeChatwootRequest> tanda) {

        ReceiveMensajeChatwootRequest ultimoMensaje = tanda.getLast();
        long conversationId = ultimoMensaje.conversation().id();
        String pregunta = tanda.stream()
                .map(ReceiveMensajeChatwootRequest::content)
                .collect(Collectors.joining("\n"));
        String numeroTelefono = findNumeroTelefono(ultimoMensaje);
        String nombreContacto = findNombreContacto(ultimoMensaje);

        log.info("Procesando tanda de Chatwoot: conversación={}, mensajes={}", conversationId, tanda.size());

        String respuestaFlowise;
        try {
            respuestaFlowise = flowiseClient.sendPreguntaFlowise(pregunta, "chatwoot-" + conversationId,
                    numeroTelefono, nombreContacto);
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
     * pendiente (manejada por el bot). El texto se evalúa aparte (los medios llegan sin texto).
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code boolean} {@code true} si el bot debe atenderlo
     */
    private boolean esMensajeParaElBot(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        boolean esEntrante = "incoming".equals(receiveMensajeChatwootRequest.messageType())
                || "0".equals(receiveMensajeChatwootRequest.messageType());

        return "message_created".equals(receiveMensajeChatwootRequest.event())
                && esEntrante
                && !Boolean.TRUE.equals(receiveMensajeChatwootRequest.privado())
                && receiveMensajeChatwootRequest.conversation() != null
                && receiveMensajeChatwootRequest.conversation().id() != null
                && "pending".equals(receiveMensajeChatwootRequest.conversation().status());

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

        RemitenteChatwootRequest contactoConversacion = findContactoConversacion(receiveMensajeChatwootRequest);
        return contactoConversacion != null ? contactoConversacion.phoneNumber() : null;

    }

    /**
     * Obtiene el nombre del contacto: el del {@code sender} y, si no lo trae, el del contacto de la
     * conversación.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code String} el nombre, o cadena vacía si el payload no lo trae
     */
    private String findNombreContacto(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        RemitenteChatwootRequest sender = receiveMensajeChatwootRequest.sender();
        if (sender != null && sender.name() != null && !sender.name().isBlank()) {
            return sender.name();
        }

        RemitenteChatwootRequest contactoConversacion = findContactoConversacion(receiveMensajeChatwootRequest);
        return contactoConversacion != null && contactoConversacion.name() != null ? contactoConversacion.name() : "";

    }

    /**
     * Obtiene el contacto dueño de la conversación ({@code conversation.meta.sender}).
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code RemitenteChatwootRequest} el contacto, o {@code null} si el payload no lo trae
     */
    private RemitenteChatwootRequest findContactoConversacion(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        var meta = receiveMensajeChatwootRequest.conversation().meta();
        return meta != null ? meta.sender() : null;

    }

    /**
     * Envía un mensaje al paciente; si Chatwoot falla, solo se loguea.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param mensaje {@code String} texto para el paciente
     */
    private void sendMensajeSeguro(long conversationId, String mensaje) {

        try {
            chatwootClient.sendMensajeChatwoot(conversationId, mensaje);
        } catch (RuntimeException excepcion) {
            log.error("Chatwoot falló al responder la conversación {}", conversationId, excepcion);
        }

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
