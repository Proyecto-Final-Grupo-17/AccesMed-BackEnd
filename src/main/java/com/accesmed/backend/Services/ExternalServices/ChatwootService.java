package com.accesmed.backend.Services.ExternalServices;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Records.Chatwoot.Request.AdjuntoChatwootRequest;
import com.accesmed.backend.Records.Chatwoot.Request.ReceiveMensajeChatwootRequest;
import com.accesmed.backend.Records.Chatwoot.Request.RemitenteChatwootRequest;
import com.accesmed.backend.Services.AgenteServices.AccionAgente;
import com.accesmed.backend.Services.Clients.ChatwootClient;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Lógica del canal Chatwoot (diseño en {@code Docs/Diseno-Chatbot.md §2.3}, contrato en
 * {@code Docs/Features/Chatbot.md §6}): interpreta el payload del webhook, decide el horario de
 * atención, el antispam y la ventana de inactividad de cada conversación, compone los mensajes
 * fijos, arma la sesión y las variables para Flowise, interpreta los tokens de su respuesta y
 * envía los mensajes por Chatwoot sin dejar que una falla de Chatwoot corte el flujo.
 * <p>
 * El estado por conversación (ventana, antispam, fuera de tema) vive en memoria: supone una sola
 * instancia del back, igual que el agrupador de ráfagas.
 */
@Slf4j
@Service
public class ChatwootService {

    //region ========== Constantes ==========

    /**
     * Token que el agente agrega a su respuesta para derivar la conversación a una persona.
     */
    static final String TOKEN_HANDOFF = "—HANDOFF—";

    /**
     * Token que el agente agrega cuando el mensaje del paciente no tiene relación con la clínica.
     */
    static final String TOKEN_FUERA_DE_TEMA = "—FUERA_DE_TEMA—";

    static final String MENSAJE_ERROR_TECNICO = "Estamos con problemas técnicos, en breve te atiende una persona.";

    static final String MENSAJE_SOLO_TEXTO = "Por ahora solo puedo leer mensajes de texto. ¿Me lo escribís?";

    /**
     * Aviso cuando el mensaje no trae texto ni audio y el audio está habilitado.
     */
    static final String MENSAJE_TEXTO_O_AUDIO =
            "Por ahora puedo leer mensajes de texto y escuchar audios. ¿Me lo escribís o me mandás un audio?";

    static final String MENSAJE_AUDIO_NO_ENTENDIDO =
            "🎧 No pude escuchar bien tu audio. ¿Me lo mandás de nuevo o me lo escribís?";

    static final String MENSAJE_AUDIO_LARGO =
            "🎧 Tu audio es un poco largo para mí. ¿Me lo mandás en uno más corto o me lo escribís?";

    /**
     * Nombre del archivo de la nota de voz de la respuesta.
     */
    static final String NOMBRE_ARCHIVO_AUDIO_RESPUESTA = "respuesta.ogg";

    static final String MENSAJE_ANTISPAM = "Recibimos muchos mensajes seguidos. Por favor, escribí tu consulta "
            + "en un solo mensaje; en unos minutos seguimos.";

    static final String MENSAJE_HANDOFF_FUERA_DE_TEMA = "Te paso con una persona del equipo.";

    /**
     * Token que el agente agrega cuando la charla terminó (el paciente se despidió): el back resuelve
     * la conversación y cierra la ventana.
     */
    static final String TOKEN_FIN = "—FIN—";

    /**
     * Aviso inmediato al despachar la tanda a Flowise (el agente tarda unos segundos en responder).
     */
    static final String MENSAJE_ESPERA = "⏳ Un momento…";

    /**
     * Cierre de una conversación del bot que quedó sin mensajes más allá de la ventana de inactividad.
     */
    static final String MENSAJE_CIERRE_INACTIVIDAD = "Cerramos esta conversación por inactividad. "
            + "Cuando quieras, escribinos de nuevo. 👋";

    static final String MENSAJE_DOCUMENTO_RECIBIDO = "📎 Recibimos tu archivo. Alguien del equipo lo va a revisar.";

    /**
     * Etiqueta de Chatwoot para las conversaciones con archivos del paciente (la crea el equipo en Chatwoot).
     */
    static final String ETIQUETA_DOCUMENTACION = "documentacion";

    /**
     * Tipos de adjunto que se toman como documento del paciente (audio y video siguen con "solo texto").
     */
    private static final Set<String> TIPOS_ADJUNTO_DOCUMENTO = Set.of("image", "file");

    /**
     * Extensión del archivo de audio a tipo MIME, para los formatos que acepta la transcripción.
     */
    private static final Map<String, String> MIME_AUDIO_POR_EXTENSION = Map.of(
            "ogg", "audio/ogg", "opus", "audio/ogg", "mp3", "audio/mpeg", "m4a", "audio/mp4",
            "mp4", "audio/mp4", "wav", "audio/wav", "webm", "audio/webm", "aac", "audio/aac");

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter FORMATO_FECHA_HORA_ACTUAL =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final ObjectMapper objectMapper;
    private final ChatwootClient chatwootClient;
    private final OpenAiService openAiService;
    private final boolean audioRespuestaConTexto;
    private final Executor chatwootTaskExecutor;
    private final Duration ventanaInactividad;
    private final int antispamMaxMensajes;
    private final Duration antispamVentana;
    private final Duration antispamSilencio;
    private final int fueraDeTemaMaxSeguidos;

    /**
     * Estado de cada conversación del bot, por id de conversación de Chatwoot.
     */
    private final Map<Long, EstadoConversacion> estadosConversacion = new ConcurrentHashMap<>();

    //endregion

    /**
     * Construye el servicio del canal con sus dependencias y la configuración de ventana y antispam.
     *
     * @param objectMapper {@code ObjectMapper} parser del payload del webhook
     * @param chatwootClient {@code ChatwootClient} cliente de la API de Chatwoot
     * @param openAiService {@code OpenAiService} audio del canal (acá solo para saber si está habilitado)
     * @param audioRespuestaConTexto {@code boolean} si la nota de voz va seguida del mismo texto ({@code accesmed.audio.respuesta-con-texto})
     * @param chatwootTaskExecutor {@code Executor} hilos de los mensajes que se envían en segundo plano
     * @param ventanaInactividadMinutos {@code long} minutos sin mensajes que cierran la ventana ({@code accesmed.chatwoot.ventana-inactividad-minutos})
     * @param antispamMaxMensajes {@code int} mensajes tolerados dentro de la ventana de antispam ({@code accesmed.chatwoot.antispam.max-mensajes})
     * @param antispamVentanaSegundos {@code long} ventana de conteo del antispam ({@code accesmed.chatwoot.antispam.ventana-segundos})
     * @param antispamSilencioMinutos {@code long} silencio después del aviso de antispam ({@code accesmed.chatwoot.antispam.silencio-minutos})
     * @param fueraDeTemaMaxSeguidos {@code int} respuestas fuera de tema seguidas que derivan a una persona ({@code accesmed.chatwoot.fuera-de-tema.max-seguidos})
     */
    public ChatwootService(ObjectMapper objectMapper, ChatwootClient chatwootClient, OpenAiService openAiService,
            @Value("${accesmed.audio.respuesta-con-texto:true}") boolean audioRespuestaConTexto,
            @Qualifier("chatwootTaskExecutor") Executor chatwootTaskExecutor,
            @Value("${accesmed.chatwoot.ventana-inactividad-minutos:30}") long ventanaInactividadMinutos,
            @Value("${accesmed.chatwoot.antispam.max-mensajes:10}") int antispamMaxMensajes,
            @Value("${accesmed.chatwoot.antispam.ventana-segundos:60}") long antispamVentanaSegundos,
            @Value("${accesmed.chatwoot.antispam.silencio-minutos:5}") long antispamSilencioMinutos,
            @Value("${accesmed.chatwoot.fuera-de-tema.max-seguidos:3}") int fueraDeTemaMaxSeguidos) {

        this.objectMapper = objectMapper;
        this.chatwootClient = chatwootClient;
        this.openAiService = openAiService;
        this.audioRespuestaConTexto = audioRespuestaConTexto;
        this.chatwootTaskExecutor = chatwootTaskExecutor;
        this.ventanaInactividad = Duration.ofMinutes(ventanaInactividadMinutos);
        this.antispamMaxMensajes = antispamMaxMensajes;
        this.antispamVentana = Duration.ofSeconds(antispamVentanaSegundos);
        this.antispamSilencio = Duration.ofMinutes(antispamSilencioMinutos);
        this.fueraDeTemaMaxSeguidos = fueraDeTemaMaxSeguidos;

    }

    /**
     * Resultado del control de antispam de un mensaje entrante.
     */
    public enum ControlSpam {

        /**
         * El mensaje sigue su curso.
         */
        PERMITIDO,

        /**
         * Este mensaje superó el umbral: se avisa una vez y empieza el silencio.
         */
        LIMITE_SUPERADO,

        /**
         * La conversación está en silencio: el mensaje se ignora.
         */
        SILENCIADO

    }

    //region ========== Métodos: payload ==========

    /**
     * Parsea el body crudo del webhook.
     *
     * @param cuerpoCrudo {@code String} body del request
     * @return {@code ReceiveMensajeChatwootRequest} el payload parseado
     * @throws ValidacionException {@code ValidacionException} si el body no es un JSON válido
     */
    public ReceiveMensajeChatwootRequest parseMensajeChatwoot(String cuerpoCrudo) {

        try {
            ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest =
                    objectMapper.readValue(cuerpoCrudo, ReceiveMensajeChatwootRequest.class);
            return receiveMensajeChatwootRequest;
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
    public boolean isMensajeParaElBot(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        boolean esEntrante = "incoming".equals(receiveMensajeChatwootRequest.messageType())
                || "0".equals(receiveMensajeChatwootRequest.messageType());

        boolean esMensajeParaElBot = "message_created".equals(receiveMensajeChatwootRequest.event())
                && esEntrante
                && !Boolean.TRUE.equals(receiveMensajeChatwootRequest.privado())
                && receiveMensajeChatwootRequest.conversation() != null
                && receiveMensajeChatwootRequest.conversation().id() != null
                && "pending".equals(receiveMensajeChatwootRequest.conversation().status());
        return esMensajeParaElBot;

    }

    /**
     * Indica si el mensaje trae texto. Los audios, imágenes, videos y stickers sin pie llegan vacíos.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code boolean} {@code true} si el mensaje tiene texto
     */
    public boolean hasTextoMensaje(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        boolean tieneTexto = receiveMensajeChatwootRequest.content() != null
                && !receiveMensajeChatwootRequest.content().isBlank();
        return tieneTexto;

    }

    /**
     * Indica si el mensaje trae un documento del paciente (imagen o archivo). Los audios y videos no
     * cuentan: siguen con el aviso de "solo texto".
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code boolean} {@code true} si trae al menos una imagen o un archivo
     */
    public boolean hasDocumentoMensaje(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        boolean tieneDocumento = receiveMensajeChatwootRequest.attachments() != null
                && receiveMensajeChatwootRequest.attachments().stream()
                        .anyMatch(adjunto -> adjunto != null && TIPOS_ADJUNTO_DOCUMENTO.contains(adjunto.fileType()));
        return tieneDocumento;

    }

    /**
     * Indica si el mensaje trae un audio del paciente (adjunto {@code file_type = audio}).
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code boolean} {@code true} si trae al menos un adjunto de audio
     */
    public boolean hasAudioMensaje(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        boolean tieneAudio = findAdjuntoAudio(receiveMensajeChatwootRequest).isPresent();
        return tieneAudio;

    }

    /**
     * Obtiene el primer adjunto de audio del mensaje.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code Optional<AdjuntoChatwootRequest>} el adjunto de audio, si el mensaje trae uno
     */
    public Optional<AdjuntoChatwootRequest> findAdjuntoAudio(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        Optional<AdjuntoChatwootRequest> adjuntoAudio = receiveMensajeChatwootRequest.attachments() == null
                ? Optional.empty()
                : receiveMensajeChatwootRequest.attachments().stream()
                        .filter(adjunto -> adjunto != null && "audio".equals(adjunto.fileType()))
                        .findFirst();
        return adjuntoAudio;

    }

    /**
     * Descarga el archivo de un adjunto de audio desde Chatwoot.
     *
     * @param adjuntoAudio {@code AdjuntoChatwootRequest} adjunto de audio del mensaje
     * @return {@code byte[]} el contenido del audio
     * @throws ValidacionException {@code ValidacionException} si el adjunto no trae URL de descarga
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public byte[] downloadAudio(AdjuntoChatwootRequest adjuntoAudio) {

        if (adjuntoAudio.dataUrl() == null || adjuntoAudio.dataUrl().isBlank()) {
            log.warn("El adjunto de audio no trae data_url: no se puede descargar");
            throw new ValidacionException(getClass(), List.of("El adjunto de audio no trae URL de descarga."));
        }

        byte[] contenidoAudio = chatwootClient.downloadAdjuntoChatwoot(adjuntoAudio.dataUrl());
        byte[] audio = contenidoAudio != null ? contenidoAudio : new byte[0];
        return audio;

    }

    /**
     * Deduce el nombre de archivo de un audio a partir de su URL (OpenAI toma el formato de la
     * extensión); si no tiene una extensión conocida, asume {@code audio.ogg} (nota de voz de WhatsApp).
     *
     * @param adjuntoAudio {@code AdjuntoChatwootRequest} adjunto de audio del mensaje
     * @return {@code String} nombre de archivo con extensión
     */
    public String findNombreArchivoAudio(AdjuntoChatwootRequest adjuntoAudio) {

        String extension = "ogg";
        try {
            String ruta = URI.create(adjuntoAudio.dataUrl()).getPath();
            int posicionPunto = ruta == null ? -1 : ruta.lastIndexOf('.');
            String extensionUrl = posicionPunto >= 0 ? ruta.substring(posicionPunto + 1).toLowerCase(Locale.ROOT) : "";
            if (MIME_AUDIO_POR_EXTENSION.containsKey(extensionUrl)) {
                extension = extensionUrl;
            }
        } catch (RuntimeException excepcion) {
            log.debug("No se pudo leer la extensión del audio, se asume ogg");
        }
        String nombreArchivoAudio = "audio." + extension;
        return nombreArchivoAudio;

    }

    /**
     * Obtiene el tipo MIME de un audio según la extensión de su nombre de archivo.
     *
     * @param nombreArchivo {@code String} nombre de archivo del audio (ej. {@code audio.ogg})
     * @return {@code String} el tipo MIME ({@code audio/ogg} por defecto)
     */
    public String findMimeAudio(String nombreArchivo) {

        String extension = nombreArchivo.substring(nombreArchivo.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mimeAudio = MIME_AUDIO_POR_EXTENSION.getOrDefault(extension, "audio/ogg");
        return mimeAudio;

    }

    /**
     * Loguea la latencia Chatwoot a back del mensaje ({@code created_at} del payload contra ahora).
     * Tolera el epoch en segundos o milisegundos y el texto ISO; si no se puede leer, no loguea y
     * nunca falla.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     */
    public void logLatenciaMensaje(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        try {
            Instant creadoEn = parseCreatedAt(receiveMensajeChatwootRequest.createdAt());
            if (creadoEn != null) {
                log.info("Latencia Chatwoot → back: conversación={}, mensaje={}, {} ms",
                        receiveMensajeChatwootRequest.conversation().id(), receiveMensajeChatwootRequest.id(),
                        Duration.between(creadoEn, Instant.now()).toMillis());
            }
        } catch (RuntimeException excepcion) {
            log.debug("No se pudo calcular la latencia del mensaje: {}", excepcion.getMessage());
        }

    }

    /**
     * Obtiene el teléfono del contacto: el del {@code sender} y, si no lo trae, el del contacto de
     * la conversación.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code String} el teléfono, o {@code null} si el payload no lo trae
     */
    public String findNumeroTelefono(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        RemitenteChatwootRequest sender = receiveMensajeChatwootRequest.sender();
        if (sender != null && sender.phoneNumber() != null && !sender.phoneNumber().isBlank()) {
            return sender.phoneNumber();
        }

        RemitenteChatwootRequest contactoConversacion = findContactoConversacion(receiveMensajeChatwootRequest);
        String numeroTelefono = contactoConversacion != null ? contactoConversacion.phoneNumber() : null;
        return numeroTelefono;

    }

    /**
     * Obtiene el nombre del contacto: el del {@code sender} y, si no lo trae, el del contacto de la
     * conversación.
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code String} el nombre, o cadena vacía si el payload no lo trae
     */
    public String findNombreContacto(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        RemitenteChatwootRequest sender = receiveMensajeChatwootRequest.sender();
        if (sender != null && sender.name() != null && !sender.name().isBlank()) {
            return sender.name();
        }

        RemitenteChatwootRequest contactoConversacion = findContactoConversacion(receiveMensajeChatwootRequest);
        String nombreContacto = contactoConversacion != null && contactoConversacion.name() != null
                ? contactoConversacion.name() : "";
        return nombreContacto;

    }

    //endregion

    //region ========== Métodos: horario, ventana y antispam ==========

    /**
     * Indica si en este momento la clínica está dentro de su horario de atención, en su zona
     * horaria. El inicio es siempre anterior al fin (lo valida {@code ClinicaDomainService}), así
     * que el rango no cruza la medianoche.
     *
     * @param clinica {@code Clinica} configuración de la clínica
     * @return {@code boolean} {@code true} si {@code inicio <= ahora < fin}
     */
    public boolean isDentroHorarioAtencion(Clinica clinica) {

        LocalTime horaActualClinica = LocalTime.now(ZoneId.of(clinica.getZonaHoraria()));

        boolean dentroHorarioAtencion = !horaActualClinica.isBefore(clinica.getHorarioInicioAtencion())
                && horaActualClinica.isBefore(clinica.getHorarioFinAtencion());
        return dentroHorarioAtencion;

    }

    /**
     * Registra un mensaje recibido fuera del horario de atención (renueva la ventana) e indica si
     * corresponde avisarle al paciente: el aviso se manda una sola vez por ventana.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @return {@code boolean} {@code true} si hay que mandar el aviso de fuera de horario
     */
    public boolean registerMensajeFueraHorario(long conversationId) {

        EstadoConversacion estadoConversacion = findEstadoConversacion(conversationId);

        synchronized (estadoConversacion) {
            renewVentana(estadoConversacion, Instant.now());
            boolean correspondeAviso = !estadoConversacion.avisoFueraHorarioEnviado;
            estadoConversacion.avisoFueraHorarioEnviado = true;
            return correspondeAviso;
        }

    }

    /**
     * Registra un mensaje recibido dentro del horario de atención: renueva la ventana y aplica el
     * antispam (más de {@code max-mensajes} en {@code ventana-segundos} silencia la conversación
     * durante {@code silencio-minutos}).
     *
     * @param conversationId {@code long} identificador de la conversación
     * @return {@code ControlSpam} si el mensaje sigue, si acaba de superar el umbral o si se ignora
     */
    public ControlSpam registerMensajeEntrante(long conversationId) {

        EstadoConversacion estadoConversacion = findEstadoConversacion(conversationId);
        Instant ahora = Instant.now();

        synchronized (estadoConversacion) {

            //Renovar la ventana: un mensaje después de la inactividad abre una sesión nueva
            renewVentana(estadoConversacion, ahora);

            //Durante el silencio no se cuenta ni se responde
            if (estadoConversacion.silenciadoHasta != null && ahora.isBefore(estadoConversacion.silenciadoHasta)) {
                return ControlSpam.SILENCIADO;
            }

            //Contar solo los mensajes dentro de la ventana de antispam
            estadoConversacion.marcasMensajes.addLast(ahora);
            Instant limiteVentana = ahora.minus(antispamVentana);
            while (!estadoConversacion.marcasMensajes.isEmpty()
                    && estadoConversacion.marcasMensajes.peekFirst().isBefore(limiteVentana)) {
                estadoConversacion.marcasMensajes.pollFirst();
            }

            //Superar el umbral silencia la conversación (el aviso se manda una sola vez)
            if (estadoConversacion.marcasMensajes.size() > antispamMaxMensajes) {
                log.warn("Antispam: la conversación {} superó {} mensajes en {} s, se silencia {} min",
                        conversationId, antispamMaxMensajes, antispamVentana.toSeconds(), antispamSilencio.toMinutes());
                estadoConversacion.silenciadoHasta = ahora.plus(antispamSilencio);
                estadoConversacion.marcasMensajes.clear();
                return ControlSpam.LIMITE_SUPERADO;
            }

            return ControlSpam.PERMITIDO;

        }

    }

    /**
     * Indica si corresponde mandar la bienvenida en esta ventana y, si corresponde, la marca como
     * enviada: se manda una sola vez por ventana, con el primer mensaje de texto.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @return {@code boolean} {@code true} si hay que mandar la bienvenida
     */
    public boolean checkBienvenidaPendiente(long conversationId) {

        EstadoConversacion estadoConversacion = findEstadoConversacion(conversationId);

        synchronized (estadoConversacion) {
            boolean bienvenidaPendiente = !estadoConversacion.bienvenidaEnviada;
            estadoConversacion.bienvenidaEnviada = true;
            return bienvenidaPendiente;
        }

    }

    /**
     * Cierra las conversaciones atendidas por el bot que quedaron sin mensajes más allá de la
     * ventana de inactividad (Diseno-Chatbot §4.8): manda un cierre fijo, resuelve la conversación
     * en Chatwoot y cierra la ventana, igual que {@code —FIN—}. No toca las derivadas a una persona
     * ni las que el bot no llegó a atender (sin bienvenida). Corre cada minuto.
     */
    @Scheduled(fixedDelayString = "PT1M")
    public void closeConversacionesInactivas() {

        Instant ahora = Instant.now();

        //Elegir las vencidas y cerrar su ventana adentro del monitor, para no cerrar dos veces
        List<Long> conversacionesACerrar = new ArrayList<>();
        estadosConversacion.forEach((conversationId, estadoConversacion) -> {
            synchronized (estadoConversacion) {
                boolean atendidaPorElBot = estadoConversacion.bienvenidaEnviada && !estadoConversacion.derivadaAPersona;
                boolean ventanaVencida = estadoConversacion.ultimoMensaje != null
                        && estadoConversacion.ultimoMensaje.plus(ventanaInactividad).isBefore(ahora);
                if (atendidaPorElBot && ventanaVencida) {
                    estadoConversacion.ultimoMensaje = null;
                    estadoConversacion.inicioVentana = null;
                    conversacionesACerrar.add(conversationId);
                }
            }
        });

        //Avisar y resolver fuera del monitor (son llamadas HTTP)
        for (long conversationId : conversacionesACerrar) {
            try {
                log.info("Cierre por inactividad: se resuelve la conversación {}", conversationId);
                chatwootClient.sendMensajeChatwoot(conversationId, MENSAJE_CIERRE_INACTIVIDAD);
                chatwootClient.resolveConversacionChatwoot(conversationId);
            } catch (RuntimeException excepcion) {
                log.error("Chatwoot falló al cerrar por inactividad la conversación {}", conversationId, excepcion);
            }
        }

    }

    /**
     * Borra el estado de las conversaciones inactivas y sin silencio vigente, para que el mapa no
     * crezca sin límite. Corre cada 10 minutos.
     */
    @Scheduled(fixedDelayString = "PT10M")
    public void removeEstadosConversacionVencidos() {

        Instant ahora = Instant.now();

        int cantidadAntes = estadosConversacion.size();
        estadosConversacion.entrySet().removeIf(entrada -> {
            EstadoConversacion estadoConversacion = entrada.getValue();
            synchronized (estadoConversacion) {
                boolean ventanaVencida = estadoConversacion.ultimoMensaje == null
                        || estadoConversacion.ultimoMensaje.plus(ventanaInactividad).isBefore(ahora);
                boolean sinSilencio = estadoConversacion.silenciadoHasta == null
                        || estadoConversacion.silenciadoHasta.isBefore(ahora);
                return ventanaVencida && sinSilencio;
            }
        });

        log.debug("Limpieza de estados de conversación de Chatwoot: {} → {}", cantidadAntes, estadosConversacion.size());

    }

    //endregion

    //region ========== Métodos: Flowise ==========

    /**
     * Arma el {@code sessionId} de Flowise para la ventana vigente de la conversación: cambia cuando
     * la ventana vence, así la memoria del agente arranca limpia después de la inactividad.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @return {@code String} sesión con el formato {@code chatwoot-<conversación>-<inicio de ventana en epoch>}
     */
    public String buildSessionIdFlowise(long conversationId) {

        EstadoConversacion estadoConversacion = findEstadoConversacion(conversationId);

        synchronized (estadoConversacion) {
            if (estadoConversacion.inicioVentana == null) {
                renewVentana(estadoConversacion, Instant.now());
            }
            String sessionIdFlowise = "chatwoot-" + conversationId + "-" + estadoConversacion.inicioVentana.getEpochSecond();
            return sessionIdFlowise;
        }

    }

    /**
     * Arma las variables de sesión que Flowise expone como {@code $vars} (contrato en
     * {@code Docs/Features/Chatbot.md §6.D}).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param nombreContacto {@code String} nombre del perfil de WhatsApp (puede ser vacío)
     * @param pacienteRegistrado {@code Optional<Paciente>} paciente activo dueño del teléfono, si existe
     * @param clinica {@code Clinica} configuración de la clínica (zona horaria)
     * @param entradaAudio {@code boolean} {@code true} si el paciente habló por audio en la tanda (la respuesta se le lee en voz alta)
     * @return {@code Map<String, String>} {@code numeroTelefono}, {@code nombreContacto}, {@code pacienteRegistrado}, {@code nombrePaciente}, {@code fechaHoraActual} y {@code entradaAudio}
     */
    public Map<String, String> buildVariablesFlowise(String numeroTelefono, String nombreContacto,
            Optional<Paciente> pacienteRegistrado, Clinica clinica, boolean entradaAudio) {

        String fechaHoraActual = FORMATO_FECHA_HORA_ACTUAL.format(ZonedDateTime.now(ZoneId.of(clinica.getZonaHoraria())));

        Map<String, String> variablesFlowise = Map.of(
                "numeroTelefono", numeroTelefono,
                "nombreContacto", nombreContacto == null ? "" : nombreContacto,
                "pacienteRegistrado", String.valueOf(pacienteRegistrado.isPresent()),
                "nombrePaciente", pacienteRegistrado.map(Paciente::getNombre).orElse(""),
                "fechaHoraActual", fechaHoraActual,
                "entradaAudio", String.valueOf(entradaAudio));
        return variablesFlowise;

    }

    /**
     * Interpreta la respuesta de Flowise y la entrega por Chatwoot. Quita los tokens antes de
     * enviar el texto. {@code —HANDOFF—} deriva la conversación a una persona.
     * {@code —FUERA_DE_TEMA—} suma al contador de la conversación y, al llegar al máximo, deriva;
     * cualquier otra respuesta reinicia el contador. {@code —FIN—} (si no hay handoff) resuelve la
     * conversación y cierra la ventana: el próximo mensaje arranca de cero, con bienvenida y sesión
     * nueva. Una respuesta vacía se trata como error técnico.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param respuestaFlowise {@code String} texto que devolvió el agente, o {@code null}
     */
    public void sendRespuestaFlowise(long conversationId, String respuestaFlowise) {

        sendRespuestaFlowise(conversationId, respuestaFlowise, null, audioRespuestaConTexto);

    }

    /**
     * Indica si la nota de voz de la respuesta va seguida del mismo texto ({@code accesmed.audio.respuesta-con-texto}).
     *
     * @return {@code boolean} {@code true} si el texto se manda también; {@code false} si va solo la voz
     */
    public boolean isAudioRespuestaConTexto() {

        return audioRespuestaConTexto;

    }

    /**
     * Quita los tokens de control ({@code —HANDOFF—}, {@code —FUERA_DE_TEMA—}, {@code —FIN—}) de la
     * respuesta de Flowise y devuelve el texto que ve el paciente.
     *
     * @param respuestaFlowise {@code String} texto que devolvió el agente, o {@code null}
     * @return {@code String} el texto sin tokens (vacío si no había texto)
     */
    public String removeTokensRespuesta(String respuestaFlowise) {

        String textoSinTokens = respuestaFlowise == null ? "" : respuestaFlowise.replace(TOKEN_HANDOFF, "")
                .replace(TOKEN_FUERA_DE_TEMA, "").replace(TOKEN_FIN, "").trim();
        return textoSinTokens;

    }

    /**
     * Igual que {@link #sendRespuestaFlowise(long, String)}, pero con la nota de voz de la respuesta:
     * sube primero el audio y después el texto (o solo el audio si {@code respuestaConTexto} es
     * {@code false}). Sin audio, manda el texto. Si el audio no se puede enviar, manda el texto igual.
     * La lógica de tokens (handoff, fuera de tema y fin) no cambia.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param respuestaFlowise {@code String} texto que devolvió el agente, o {@code null}
     * @param audioRespuesta {@code byte[]} nota de voz OGG/Opus de la respuesta, o {@code null} si va solo texto
     * @param respuestaConTexto {@code boolean} {@code true} si el texto va después del audio
     */
    public void sendRespuestaFlowise(long conversationId, String respuestaFlowise, byte[] audioRespuesta,
            boolean respuestaConTexto) {

        //Sin respuesta no hay qué decirle al paciente: lo atiende una persona
        if (respuestaFlowise == null || respuestaFlowise.isBlank()) {
            log.error("Flowise devolvió una respuesta vacía para la conversación {}", conversationId);
            transferConversacionPorErrorTecnico(conversationId);
            return;
        }

        boolean pideHandoff = respuestaFlowise.contains(TOKEN_HANDOFF);
        boolean fueraDeTema = respuestaFlowise.contains(TOKEN_FUERA_DE_TEMA);
        boolean terminaCharla = respuestaFlowise.contains(TOKEN_FIN) && !pideHandoff;
        String textoRespuesta = removeTokensRespuesta(respuestaFlowise);

        //Contar los fuera de tema seguidos: al llegar al máximo, la conversación pasa a una persona
        boolean derivaPorFueraDeTema = false;
        EstadoConversacion estadoConversacion = findEstadoConversacion(conversationId);
        synchronized (estadoConversacion) {
            if (fueraDeTema && !pideHandoff) {
                estadoConversacion.fueraDeTemaSeguidos++;
                derivaPorFueraDeTema = estadoConversacion.fueraDeTemaSeguidos >= fueraDeTemaMaxSeguidos;
            }
            if (!fueraDeTema || derivaPorFueraDeTema) {
                estadoConversacion.fueraDeTemaSeguidos = 0;
            }
            //Fin de la charla: cerrar la ventana para que el próximo mensaje abra una sesión nueva
            if (terminaCharla) {
                estadoConversacion.ultimoMensaje = null;
                estadoConversacion.inicioVentana = null;
            }
            //Derivada a una persona: el cierre por inactividad ya no es del bot
            if (pideHandoff || derivaPorFueraDeTema) {
                estadoConversacion.derivadaAPersona = true;
            }
        }

        //La nota de voz va primero; si falla, el texto sale igual
        boolean audioEnviado = false;
        if (audioRespuesta != null && audioRespuesta.length > 0 && !textoRespuesta.isEmpty()) {
            try {
                chatwootClient.sendAudioChatwoot(conversationId, audioRespuesta, NOMBRE_ARCHIVO_AUDIO_RESPUESTA);
                audioEnviado = true;
            } catch (RuntimeException excepcion) {
                log.error("Chatwoot falló al enviar la nota de voz a la conversación {}: se manda el texto", conversationId, excepcion);
            }
        }

        //Entregar el texto y, si corresponde, derivar
        try {
            if (!textoRespuesta.isEmpty() && (!audioEnviado || respuestaConTexto)) {
                chatwootClient.sendMensajeChatwoot(conversationId, textoRespuesta);
            }
            if (derivaPorFueraDeTema) {
                log.info("Handoff por {} mensajes fuera de tema seguidos: conversación={}", fueraDeTemaMaxSeguidos, conversationId);
                chatwootClient.sendMensajeChatwoot(conversationId, MENSAJE_HANDOFF_FUERA_DE_TEMA);
            }
            if (pideHandoff || derivaPorFueraDeTema) {
                log.info("Handoff a una persona: conversación={}", conversationId);
                chatwootClient.openConversacionChatwoot(conversationId);
            } else if (terminaCharla) {
                log.info("Fin de la charla: se resuelve la conversación {}", conversationId);
                chatwootClient.resolveConversacionChatwoot(conversationId);
            }
        } catch (RuntimeException excepcion) {
            log.error("Chatwoot falló al responder la conversación {}", conversationId, excepcion);
        }

    }

    //endregion

    //region ========== Métodos: envíos ==========

    /**
     * Avisa, en segundo plano, el horario de atención a quien escribe fuera de él.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param clinica {@code Clinica} configuración de la clínica (horario)
     * @param pacienteRegistrado {@code Optional<Paciente>} paciente activo dueño del teléfono, si existe (se saluda por su nombre)
     */
    public void sendMensajeFueraHorarioAsync(long conversationId, Clinica clinica, Optional<Paciente> pacienteRegistrado) {

        String saludo = pacienteRegistrado.map(paciente -> "¡Hola, " + paciente.getNombre() + "!").orElse("¡Hola!");
        String mensajeFueraHorario = saludo + " Lo sentimos, en este momento no estamos atendiendo. "
                + "Nuestro horario de atención es de " + buildRangoHorarioAtencion(clinica)
                + ". Escribinos en ese horario y con gusto te ayudamos. 🙂";
        executeEnviosAsync(conversationId, () -> sendMensajeSeguro(conversationId, mensajeFueraHorario));

    }

    /**
     * Compone la bienvenida de la ventana: asistente virtual de la clínica y horario de atención.
     * Saluda por el nombre solo si el paciente está registrado. Si el nombre de la clínica ya
     * empieza con "clínica" (sin importar mayúsculas ni tildes), no repite la palabra.
     *
     * @param clinica {@code Clinica} configuración de la clínica (nombre y horario)
     * @param pacienteRegistrado {@code Optional<Paciente>} paciente activo dueño del teléfono, si existe
     * @return {@code String} el texto de la bienvenida
     */
    public String buildMensajeBienvenida(Clinica clinica, Optional<Paciente> pacienteRegistrado) {

        String saludo = pacienteRegistrado.map(paciente -> "¡Hola, " + paciente.getNombre() + "! 👋").orElse("¡Hola! 👋");
        String nombreClinica = clinica.getNombre().trim();
        String nombreSinTildes = Normalizer.normalize(nombreClinica, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String clinicaConArticulo = nombreSinTildes.startsWith("clinica") ? nombreClinica : "clínica " + nombreClinica;
        String mensajeBienvenida = saludo + " Soy el asistente virtual de la " + clinicaConArticulo
                + ". Nuestro horario de atención es de " + buildRangoHorarioAtencion(clinica) + ".";
        return mensajeBienvenida;

    }

    /**
     * Manda, en segundo plano y en una sola tarea (para que lleguen en orden), la bienvenida de la
     * ventana (si corresponde) y el aviso "Un momento" (si el mensaje abrió una tanda nueva;
     * Diseno-Chatbot §5, D41 y D42).
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param mensajeBienvenida {@code String} bienvenida a mandar, o {@code null} si no corresponde
     * @param avisoEspera {@code boolean} {@code true} si hay que mandar "Un momento"
     */
    public void sendMensajesInicioTandaAsync(long conversationId, String mensajeBienvenida, boolean avisoEspera) {

        if (mensajeBienvenida == null && !avisoEspera) {
            return;
        }
        executeEnviosAsync(conversationId, () -> {
            if (mensajeBienvenida != null) {
                sendMensajeSeguro(conversationId, mensajeBienvenida);
            }
            if (avisoEspera) {
                sendMensajeSeguro(conversationId, MENSAJE_ESPERA);
            }
        });

    }

    /**
     * Avisa, en segundo plano, que el audio no se pudo entender.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void sendMensajeAudioNoEntendidoAsync(long conversationId) {

        executeEnviosAsync(conversationId, () -> sendMensajeSeguro(conversationId, MENSAJE_AUDIO_NO_ENTENDIDO));

    }

    /**
     * Avisa, en segundo plano, que el audio es demasiado largo.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void sendMensajeAudioLargoAsync(long conversationId) {

        executeEnviosAsync(conversationId, () -> sendMensajeSeguro(conversationId, MENSAJE_AUDIO_LARGO));

    }

    /**
     * Deja, en segundo plano, la transcripción de un audio como nota privada de la conversación
     * (la ve el equipo, no el paciente).
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param textoTranscripto {@code String} texto transcripto del audio
     */
    public void sendNotaTranscripcionAsync(long conversationId, String textoTranscripto) {

        executeEnviosAsync(conversationId, () -> {
            try {
                chatwootClient.sendNotaPrivadaChatwoot(conversationId, "🎤 Transcripción: " + textoTranscripto);
            } catch (RuntimeException excepcion) {
                log.error("Chatwoot falló al dejar la nota de transcripción en la conversación {}", conversationId, excepcion);
            }
        });

    }

    /**
     * Pide, en segundo plano, que el paciente no mande tantos mensajes seguidos.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void sendMensajeAntispamAsync(long conversationId) {

        executeEnviosAsync(conversationId, () -> sendMensajeSeguro(conversationId, MENSAJE_ANTISPAM));

    }

    /**
     * Avisa, en segundo plano, qué tipos de mensaje lee el bot: "texto o audio" si el audio está
     * habilitado, "solo texto" si no.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void sendMensajeSoloTextoAsync(long conversationId) {

        String mensajeMedioNoSoportado = openAiService.isAudioHabilitado() ? MENSAJE_TEXTO_O_AUDIO : MENSAJE_SOLO_TEXTO;
        executeEnviosAsync(conversationId, () -> sendMensajeSeguro(conversationId, mensajeMedioNoSoportado));

    }

    /**
     * Avisa, en segundo plano, que se recibió el archivo del paciente y etiqueta la conversación
     * para que el equipo lo revise. El archivo queda en Chatwoot: no se le pasa a Flowise.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void receiveDocumentoAsync(long conversationId) {

        executeEnviosAsync(conversationId, () -> {
            sendMensajeSeguro(conversationId, MENSAJE_DOCUMENTO_RECIBIDO);
            try {
                chatwootClient.addEtiquetasConversacionChatwoot(conversationId, List.of(ETIQUETA_DOCUMENTACION));
            } catch (RuntimeException excepcion) {
                log.error("Chatwoot falló al etiquetar la conversación {} con {}", conversationId, ETIQUETA_DOCUMENTACION, excepcion);
            }
        });

    }

    /**
     * Manda, en segundo plano, el aviso de espera de la acción que pidió el agente ("Buscando
     * horarios disponibles…"), para que el paciente sepa que su pedido se está procesando.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param accionAgente {@code AccionAgente} acción que el agente pidió
     */
    public void sendMensajeEsperaAsync(long conversationId, AccionAgente accionAgente) {

        String mensajeEspera = buildMensajeEspera(accionAgente);
        executeEnviosAsync(conversationId, () -> sendMensajeSeguro(conversationId, mensajeEspera));

    }

    /**
     * Avisa el problema técnico y pasa la conversación a una persona, en segundo plano.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void transferConversacionPorErrorTecnicoAsync(long conversationId) {

        executeEnviosAsync(conversationId, () -> transferConversacionPorErrorTecnico(conversationId));

    }

    /**
     * Avisa el problema técnico y pasa la conversación a una persona. Si Chatwoot falla, solo se loguea.
     *
     * @param conversationId {@code long} identificador de la conversación
     */
    public void transferConversacionPorErrorTecnico(long conversationId) {

        //Derivada a una persona: el cierre por inactividad ya no es del bot
        EstadoConversacion estadoConversacion = findEstadoConversacion(conversationId);
        synchronized (estadoConversacion) {
            estadoConversacion.derivadaAPersona = true;
        }

        try {
            chatwootClient.sendMensajeChatwoot(conversationId, MENSAJE_ERROR_TECNICO);
            chatwootClient.openConversacionChatwoot(conversationId);
        } catch (RuntimeException excepcion) {
            log.error("Chatwoot falló al derivar la conversación {} a una persona", conversationId, excepcion);
        }

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Obtiene el estado de la conversación, creándolo si es la primera vez que escribe.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @return {@code EstadoConversacion} el estado de la conversación
     */
    private EstadoConversacion findEstadoConversacion(long conversationId) {

        EstadoConversacion estadoConversacion = estadosConversacion.computeIfAbsent(conversationId,
                id -> new EstadoConversacion());
        return estadoConversacion;

    }

    /**
     * Renueva la ventana de inactividad. Si venció (o es la primera), abre una nueva: reinicia la
     * bienvenida, el aviso de fuera de horario y el contador de fuera de tema. Se llama con el
     * monitor del estado tomado.
     *
     * @param estadoConversacion {@code EstadoConversacion} estado de la conversación
     * @param ahora {@code Instant} momento del mensaje
     */
    private void renewVentana(EstadoConversacion estadoConversacion, Instant ahora) {

        boolean ventanaVencida = estadoConversacion.ultimoMensaje == null
                || estadoConversacion.ultimoMensaje.plus(ventanaInactividad).isBefore(ahora);
        if (ventanaVencida) {
            estadoConversacion.inicioVentana = ahora;
            estadoConversacion.bienvenidaEnviada = false;
            estadoConversacion.avisoFueraHorarioEnviado = false;
            estadoConversacion.fueraDeTemaSeguidos = 0;
            estadoConversacion.derivadaAPersona = false;
        }
        estadoConversacion.ultimoMensaje = ahora;

    }

    /**
     * Compone el aviso de espera de cada acción del agente.
     *
     * @param accionAgente {@code AccionAgente} acción pedida
     * @return {@code String} el aviso para el paciente
     */
    private String buildMensajeEspera(AccionAgente accionAgente) {

        String mensajeEspera = switch (accionAgente) {
            case RESERVA_TURNO -> "⏳ Reservando tu turno…";
            case CANCELACION_TURNO -> "⏳ Cancelando tu turno…";
            case REPROGRAMACION_TURNO -> "⏳ Reprogramando tu turno…";
            case CONFIRMACION_TURNO -> "⏳ Confirmando tu turno…";
            case REGISTRO_PACIENTE, MODIFICACION_PACIENTE, BAJA_PACIENTE -> "⏳ Guardando tus datos…";
        };
        return mensajeEspera;

    }

    /**
     * Formatea el horario de atención de la clínica ({@code 08:00 a 20:00 h}).
     *
     * @param clinica {@code Clinica} configuración de la clínica
     * @return {@code String} el rango horario legible
     */
    private String buildRangoHorarioAtencion(Clinica clinica) {

        String rangoHorarioAtencion = FORMATO_HORA.format(clinica.getHorarioInicioAtencion()) + " a "
                + FORMATO_HORA.format(clinica.getHorarioFinAtencion()) + " h";
        return rangoHorarioAtencion;

    }

    /**
     * Ejecuta un envío en el executor del canal. Si el executor está saturado, el envío se descarta
     * y se loguea: el webhook igual responde 200 (si diera 500, Chatwoot reintentaría el evento).
     *
     * @param conversationId {@code long} identificador de la conversación (solo para el log)
     * @param envio {@code Runnable} envío a ejecutar
     */
    private void executeEnviosAsync(long conversationId, Runnable envio) {

        try {
            chatwootTaskExecutor.execute(envio);
        } catch (RejectedExecutionException excepcion) {
            log.error("Executor saturado: se descarta un envío a la conversación {}", conversationId, excepcion);
        }

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
     * Interpreta el {@code created_at} del payload: epoch en segundos o milisegundos (numérico o en
     * texto) o fecha ISO.
     *
     * @param createdAt {@code JsonNode} valor del payload, o {@code null}
     * @return {@code Instant} el momento de creación, o {@code null} si no se puede interpretar
     */
    private Instant parseCreatedAt(JsonNode createdAt) {

        if (createdAt == null || createdAt.isNull() || createdAt.isMissingNode()) {
            return null;
        }
        String valor = createdAt.asString().trim();
        if (valor.isEmpty()) {
            return null;
        }
        try {
            double epoch = Double.parseDouble(valor);
            Instant creadoEn = epoch > 100_000_000_000d ? Instant.ofEpochMilli((long) epoch)
                    : Instant.ofEpochMilli((long) (epoch * 1000));
            return creadoEn;
        } catch (NumberFormatException excepcion) {
            //No es numérico: se prueba como fecha ISO
        }
        try {
            Instant creadoEnIso = OffsetDateTime.parse(valor).toInstant();
            return creadoEnIso;
        } catch (RuntimeException excepcion) {
            return null;
        }

    }

    /**
     * Obtiene el contacto dueño de la conversación ({@code conversation.meta.sender}).
     *
     * @param receiveMensajeChatwootRequest {@code ReceiveMensajeChatwootRequest} payload parseado
     * @return {@code RemitenteChatwootRequest} el contacto, o {@code null} si el payload no lo trae
     */
    private RemitenteChatwootRequest findContactoConversacion(ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest) {

        var meta = receiveMensajeChatwootRequest.conversation().meta();
        RemitenteChatwootRequest contactoConversacion = meta != null ? meta.sender() : null;
        return contactoConversacion;

    }

    //endregion

    /**
     * Estado en memoria de una conversación. Se lee y modifica con su propio monitor tomado.
     */
    private static final class EstadoConversacion {

        private Instant inicioVentana;
        private Instant ultimoMensaje;
        private boolean bienvenidaEnviada;
        private boolean avisoFueraHorarioEnviado;
        private final Deque<Instant> marcasMensajes = new ArrayDeque<>();
        private Instant silenciadoHasta;
        private int fueraDeTemaSeguidos;
        private boolean derivadaAPersona;

    }

}
