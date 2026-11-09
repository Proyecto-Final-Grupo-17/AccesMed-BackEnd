package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Records.Chatwoot.Request.AdjuntoChatwootRequest;
import com.accesmed.backend.Records.Chatwoot.Request.ReceiveMensajeChatwootRequest;
import com.accesmed.backend.Services.AgenteServices.TandaAgenteService;
import com.accesmed.backend.Services.Clients.FlowiseClient;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.ExternalServices.ChatwootService;
import com.accesmed.backend.Services.ExternalServices.OpenAiService;
import com.accesmed.backend.Services.Utils.AgrupadorMensajes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caso de uso del pasamanos Chatwoot, Flowise y Chatwoot (diseño en
 * {@code Docs/Diseno-Chatbot.md §2.3}, contrato en {@code Docs/Features/Chatbot.md §6}): recibe el
 * webhook del Agent Bot (la firma ya la verificó {@code ChatwootFirmaFilter}) y orquesta los
 * controles del canal en orden: horario de atención, antispam, documentos, texto o audio y
 * bienvenida, y avisa "Un momento" al abrir cada tanda. Después agrupa los mensajes en ráfaga y, en segundo plano, identifica al paciente,
 * consulta a Flowise, verifica la respuesta ({@code TandaAgenteService}) y la entrega. La lógica del
 * canal vive en {@code ChatwootService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatwootApp {

    //region ========== Dependencias o inyecciones ==========

    private final ChatwootService chatwootService;
    private final OpenAiService openAiService;
    private final FlowiseClient flowiseClient;
    private final ClinicaDomainService clinicaDomainService;
    private final PacienteDomainService pacienteDomainService;
    private final TandaAgenteService tandaAgenteService;
    private final AgrupadorMensajes<ReceiveMensajeChatwootRequest> agrupadorMensajesChatwoot;

    //endregion

    //region ========== Métodos ==========

    /**
     * Recibe un evento del webhook de Chatwoot. Si es un mensaje entrante del paciente en una
     * conversación del bot, aplica los controles del canal y, si los pasa, lo suma a la tanda de
     * su conversación. Cualquier otro evento se ignora.
     *
     * @param cuerpoCrudo {@code String} body del request sin parsear
     * @param idEntrega {@code String} header {@code X-Chatwoot-Delivery} (solo para el log), o {@code null}
     * @throws ValidacionException {@code ValidacionException} si el body no es un JSON válido
     */
    public void receiveMensajeChatwoot(String cuerpoCrudo, String idEntrega) {

        //Parsear el payload (los campos desconocidos se ignoran)
        ReceiveMensajeChatwootRequest receiveMensajeChatwootRequest = chatwootService.parseMensajeChatwoot(cuerpoCrudo);

        //Procesar solo los mensajes entrantes del paciente en conversaciones del bot
        if (!chatwootService.isMensajeParaElBot(receiveMensajeChatwootRequest)) {
            log.debug("Evento de Chatwoot ignorado: event={}, messageType={}, entrega={}",
                    receiveMensajeChatwootRequest.event(), receiveMensajeChatwootRequest.messageType(), idEntrega);
            return;
        }

        long conversationId = receiveMensajeChatwootRequest.conversation().id();
        log.info("Mensaje de Chatwoot recibido: conversación={}, mensaje={}, entrega={}",
                conversationId, receiveMensajeChatwootRequest.id(), idEntrega);
        chatwootService.logLatenciaMensaje(receiveMensajeChatwootRequest);

        //El teléfono verificado del canal: sin él no se puede identificar al paciente
        String numeroTelefono = chatwootService.findNumeroTelefono(receiveMensajeChatwootRequest);
        if (numeroTelefono == null || numeroTelefono.isBlank()) {
            log.warn("El mensaje de la conversación {} no trae el teléfono del contacto: se deriva a una persona",
                    conversationId);
            chatwootService.transferConversacionPorErrorTecnicoAsync(conversationId);
            return;
        }

        //Fuera del horario de atención el bot no atiende: avisa una vez por ventana y no llama a Flowise
        Clinica clinica = clinicaDomainService.findClinica();
        if (!chatwootService.isDentroHorarioAtencion(clinica)) {
            log.info("Mensaje fuera del horario de atención en la conversación {}", conversationId);
            if (chatwootService.registerMensajeFueraHorario(conversationId)) {
                Optional<Paciente> pacienteFueraHorario = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);
                chatwootService.sendMensajeFueraHorarioAsync(conversationId, clinica, pacienteFueraHorario);
            }
            return;
        }

        //Antispam: superar el umbral avisa una vez y silencia la conversación un rato
        ChatwootService.ControlSpam controlSpam = chatwootService.registerMensajeEntrante(conversationId);
        if (controlSpam == ChatwootService.ControlSpam.SILENCIADO) {
            log.debug("Mensaje ignorado por antispam en la conversación {}", conversationId);
            return;
        }
        if (controlSpam == ChatwootService.ControlSpam.LIMITE_SUPERADO) {
            chatwootService.sendMensajeAntispamAsync(conversationId);
            return;
        }

        //Imagen o archivo: es un documento para el equipo, no para el agente (no le llega a Flowise)
        boolean traeDocumento = chatwootService.hasDocumentoMensaje(receiveMensajeChatwootRequest);
        if (traeDocumento) {
            log.info("Documento recibido en la conversación {}: se avisa y se etiqueta", conversationId);
            chatwootService.receiveDocumentoAsync(conversationId);
        }

        //Entrada válida: texto o, con el audio habilitado, un audio (aunque no traiga texto)
        boolean traeAudio = openAiService.isAudioHabilitado() && chatwootService.hasAudioMensaje(receiveMensajeChatwootRequest);
        if (!chatwootService.hasTextoMensaje(receiveMensajeChatwootRequest) && !traeAudio) {
            //Si era un documento ya se respondió; video o sticker piden texto (o audio, si está habilitado)
            if (!traeDocumento) {
                log.info("Mensaje sin texto ni audio en la conversación {}: se pide que lo escriba", conversationId);
                chatwootService.sendMensajeSoloTextoAsync(conversationId);
            }
            return;
        }

        //Primer mensaje aceptado de la ventana (texto o audio): bienvenida inmediata (Flowise tarda y no saluda)
        String mensajeBienvenida = null;
        if (chatwootService.checkBienvenidaPendiente(conversationId)) {
            Optional<Paciente> pacienteRegistrado = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);
            mensajeBienvenida = chatwootService.buildMensajeBienvenida(clinica, pacienteRegistrado);
        }

        //Agrupar la ráfaga: la tanda se procesa en segundo plano (el webhook no espera al LLM)
        boolean abreTandaNueva = agrupadorMensajesChatwoot.addElemento(
                conversationId, receiveMensajeChatwootRequest, this::processTandaChatwoot);

        //Bienvenida y "Un momento" en una sola tarea, en orden: "Un momento" solo con el mensaje que abre la tanda
        chatwootService.sendMensajesInicioTandaAsync(conversationId, mensajeBienvenida, abreTandaNueva);

    }

    /**
     * Procesa una tanda de mensajes de una conversación: identifica al paciente, une los mensajes
     * en una sola pregunta, consulta a Flowise con la sesión de la ventana y las variables del
     * canal y la entrega. Los audios (con el audio habilitado) se transcriben antes, en orden, y la
     * transcripción queda como nota privada; si hubo audio, la respuesta también va en nota de voz
     * ({@code Docs/Diseno-Chatbot.md §5}). Mientras Flowise trabaja, la tanda queda abierta en {@code TandaAgenteService} (aviso de las escrituras). La
     * verificación de la respuesta contra la base la hace el flow ({@code Docs/Diseno-Chatbot.md §4.3}).
     * Si algo falla antes de tener respuesta, avisa al paciente y deriva la conversación a una persona.
     *
     * @param tanda {@code List<ReceiveMensajeChatwootRequest>} mensajes de la misma conversación, en orden de llegada
     */
    void processTandaChatwoot(List<ReceiveMensajeChatwootRequest> tanda) {

        ReceiveMensajeChatwootRequest ultimoMensaje = tanda.getLast();
        long conversationId = ultimoMensaje.conversation().id();
        String numeroTelefono = chatwootService.findNumeroTelefono(ultimoMensaje);

        log.info("Procesando tanda de Chatwoot: conversación={}, mensajes={}", conversationId, tanda.size());

        String respuestaFlowise;
        boolean entradaAudio = false;
        try {

            //Identificar al paciente ahora (dato fresco: pudo registrarse en la tanda anterior)
            String nombreContacto = chatwootService.findNombreContacto(ultimoMensaje);
            Clinica clinica = clinicaDomainService.findClinica();
            Optional<Paciente> pacienteRegistrado = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);

            //Armar la pregunta, la sesión de la ventana y las variables del canal
            //(los audios se transcriben en orden; un audio que falla se avisa y se omite, sin derivar)
            List<String> textosTanda = new ArrayList<>();
            for (ReceiveMensajeChatwootRequest mensaje : tanda) {
                if (openAiService.isAudioHabilitado()) {
                    Optional<AdjuntoChatwootRequest> adjuntoAudio = chatwootService.findAdjuntoAudio(mensaje);
                    if (adjuntoAudio.isPresent()) {
                        Optional<String> textoTranscripto = transcribeAudioTanda(conversationId, adjuntoAudio.get());
                        if (textoTranscripto.isPresent()) {
                            textosTanda.add(textoTranscripto.get());
                            entradaAudio = true;
                        }
                    }
                }
                if (chatwootService.hasTextoMensaje(mensaje)) {
                    textosTanda.add(mensaje.content());
                }
            }
            String pregunta = String.join("\n", textosTanda);

            //Sin texto ni audio entendido no hay nada que preguntarle al agente
            if (pregunta.isBlank()) {
                log.info("La tanda de la conversación {} quedó sin texto: no se consulta a Flowise", conversationId);
                return;
            }

            String sessionIdFlowise = chatwootService.buildSessionIdFlowise(conversationId);
            Map<String, String> variablesFlowise = chatwootService.buildVariablesFlowise(
                    numeroTelefono, nombreContacto, pacienteRegistrado, clinica, entradaAudio);

            //Abrir la tanda: las tools que llame el agente se registran contra este teléfono
            tandaAgenteService.openTanda(numeroTelefono, conversationId);

            //Consultar al agente ("Un momento" ya salió al recibir el mensaje que abrió la tanda)
            respuestaFlowise = flowiseClient.sendPreguntaFlowise(pregunta, sessionIdFlowise, variablesFlowise);

        } catch (RuntimeException excepcion) {
            log.error("No se pudo obtener la respuesta del agente para la conversación {}", conversationId, excepcion);
            chatwootService.transferConversacionPorErrorTecnico(conversationId);
            return;
        } finally {
            tandaAgenteService.closeTanda(numeroTelefono);
        }

        //Entregar la respuesta (los tokens de handoff, fuera de tema y fin los interpreta el service)
        //Si el paciente habló por audio, la respuesta también va en una nota de voz (si falla, sale solo el texto)
        byte[] audioRespuesta = null;
        String textoRespuesta = chatwootService.removeTokensRespuesta(respuestaFlowise);
        if (entradaAudio && !textoRespuesta.isEmpty()) {
            try {
                audioRespuesta = openAiService.createVozRespuesta(textoRespuesta, chatwootService.isAudioRespuestaConTexto()).orElse(null);
            } catch (RuntimeException excepcion) {
                log.error("No se pudo generar la voz de la respuesta de la conversación {}: se manda solo el texto",
                        conversationId, excepcion);
            }
        }
        chatwootService.sendRespuestaFlowise(conversationId, respuestaFlowise, audioRespuesta, chatwootService.isAudioRespuestaConTexto());

    }

    /**
     * Descarga y transcribe el audio de un mensaje de la tanda y deja la transcripción como nota
     * privada. Si algo falla (descarga, tamaño o transcripción vacía) avisa al paciente y devuelve
     * vacío: ese audio se omite, pero la tanda no se deriva.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param adjuntoAudio {@code AdjuntoChatwootRequest} adjunto de audio del mensaje
     * @return {@code Optional<String>} el texto transcripto, o vacío si el audio no se pudo usar
     */
    private Optional<String> transcribeAudioTanda(long conversationId, AdjuntoChatwootRequest adjuntoAudio) {

        //Chatwoot informa el tamaño: evita descargar un audio que de todos modos se rechaza
        if (adjuntoAudio.fileSize() != null && openAiService.isAudioDemasiadoLargo(adjuntoAudio.fileSize())) {
            log.warn("Audio demasiado largo en la conversación {}: {} bytes", conversationId, adjuntoAudio.fileSize());
            chatwootService.sendMensajeAudioLargoAsync(conversationId);
            return Optional.empty();
        }

        try {
            byte[] audio = chatwootService.downloadAudio(adjuntoAudio);
            String nombreArchivo = chatwootService.findNombreArchivoAudio(adjuntoAudio);
            String textoTranscripto = openAiService.transcribeAudioMensaje(
                    audio, nombreArchivo, chatwootService.findMimeAudio(nombreArchivo));
            chatwootService.sendNotaTranscripcionAsync(conversationId, textoTranscripto);
            return Optional.of(textoTranscripto);
        } catch (ReglaNegocioException excepcion) {
            //Ya la logueó OpenAiService en el throw: acá solo se traduce al aviso
            if (OpenAiService.CODIGO_AUDIO_DEMASIADO_LARGO.equals(excepcion.getCodigo())) {
                chatwootService.sendMensajeAudioLargoAsync(conversationId);
            } else {
                chatwootService.sendMensajeAudioNoEntendidoAsync(conversationId);
            }
            return Optional.empty();
        } catch (RuntimeException excepcion) {
            log.warn("No se pudo transcribir un audio de la conversación {}: {}", conversationId, excepcion.getMessage());
            chatwootService.sendMensajeAudioNoEntendidoAsync(conversationId);
            return Optional.empty();
        }

    }

    //endregion

}
