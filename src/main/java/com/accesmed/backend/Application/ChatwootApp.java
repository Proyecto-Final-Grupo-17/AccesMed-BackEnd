package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Records.Chatwoot.Request.ReceiveMensajeChatwootRequest;
import com.accesmed.backend.Services.AgenteServices.TandaAgenteService;
import com.accesmed.backend.Services.Clients.FlowiseClient;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.ExternalServices.ChatwootService;
import com.accesmed.backend.Services.Utils.AgrupadorMensajes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Caso de uso del pasamanos Chatwoot, Flowise y Chatwoot (diseño en
 * {@code Docs/Diseno-Chatbot.md §2.3}, contrato en {@code Docs/Features/Chatbot.md §6}): recibe el
 * webhook del Agent Bot (la firma ya la verificó {@code ChatwootFirmaFilter}) y orquesta los
 * controles del canal en orden: horario de atención, antispam, documentos, solo texto y
 * bienvenida. Después agrupa los mensajes en ráfaga y, en segundo plano, identifica al paciente,
 * consulta a Flowise, verifica la respuesta ({@code TandaAgenteService}) y la entrega. La lógica del
 * canal vive en {@code ChatwootService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatwootApp {

    //region ========== Dependencias o inyecciones ==========

    private final ChatwootService chatwootService;
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
                chatwootService.sendMensajeFueraHorarioAsync(conversationId, clinica);
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

        //Sin texto: si era un documento ya se respondió; audio, video o sticker piden que lo escriba
        if (!chatwootService.hasTextoMensaje(receiveMensajeChatwootRequest)) {
            if (!traeDocumento) {
                log.info("Mensaje sin texto en la conversación {}: se pide que lo escriba", conversationId);
                chatwootService.sendMensajeSoloTextoAsync(conversationId);
            }
            return;
        }

        //Primer mensaje de texto de la ventana: bienvenida inmediata (Flowise tarda y no saluda)
        if (chatwootService.checkBienvenidaPendiente(conversationId)) {
            Optional<Paciente> pacienteRegistrado = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);
            chatwootService.sendMensajeBienvenidaAsync(conversationId, clinica, pacienteRegistrado);
        }

        //Agrupar la ráfaga: la tanda se procesa en segundo plano (el webhook no espera al LLM)
        agrupadorMensajesChatwoot.addElemento(conversationId, receiveMensajeChatwootRequest, this::processTandaChatwoot);

    }

    /**
     * Procesa una tanda de mensajes de una conversación: identifica al paciente, une los mensajes
     * en una sola pregunta, consulta a Flowise con la sesión de la ventana y las variables del
     * canal y la entrega. Apenas se despacha la tanda manda el aviso "Un momento…"; mientras Flowise
     * trabaja, la tanda queda abierta en {@code TandaAgenteService} (aviso de las escrituras). La
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
        try {

            //Identificar al paciente ahora (dato fresco: pudo registrarse en la tanda anterior)
            String nombreContacto = chatwootService.findNombreContacto(ultimoMensaje);
            Clinica clinica = clinicaDomainService.findClinica();
            Optional<Paciente> pacienteRegistrado = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);

            //Armar la pregunta, la sesión de la ventana y las variables del canal
            String pregunta = tanda.stream()
                    .map(ReceiveMensajeChatwootRequest::content)
                    .collect(Collectors.joining("\n"));
            String sessionIdFlowise = chatwootService.buildSessionIdFlowise(conversationId);
            Map<String, String> variablesFlowise = chatwootService.buildVariablesFlowise(
                    numeroTelefono, nombreContacto, pacienteRegistrado, clinica);

            //Abrir la tanda: las tools que llame el agente se registran contra este teléfono
            tandaAgenteService.openTanda(numeroTelefono, conversationId);

            //Aviso inmediato: Flowise tarda unos segundos y el paciente tiene que saber que se está procesando
            chatwootService.sendMensajeEsperaGenericoAsync(conversationId);

            //Consultar al agente
            respuestaFlowise = flowiseClient.sendPreguntaFlowise(pregunta, sessionIdFlowise, variablesFlowise);

        } catch (RuntimeException excepcion) {
            log.error("No se pudo obtener la respuesta del agente para la conversación {}", conversationId, excepcion);
            chatwootService.transferConversacionPorErrorTecnico(conversationId);
            return;
        } finally {
            tandaAgenteService.closeTanda(numeroTelefono);
        }

        //Entregar la respuesta (los tokens de handoff, fuera de tema y fin los interpreta el service)
        chatwootService.sendRespuestaFlowise(conversationId, respuestaFlowise);

    }

    //endregion

}
