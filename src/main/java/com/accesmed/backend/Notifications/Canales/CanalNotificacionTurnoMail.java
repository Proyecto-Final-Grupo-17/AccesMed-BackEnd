package com.accesmed.backend.Notifications.Canales;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.Utils.FormatoMensaje;
import com.accesmed.backend.Services.Utils.MailService;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml.ContenidoMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de {@link CanalNotificacionTurno} que arma el mensaje según el
 * {@link TipoNotificacionTurno} y lo despacha por correo electrónico, en HTML con la marca de
 * AccesMed y los datos de la clínica en el pie (ver {@link PlantillaMailHtml}). Implementa
 * el mismo contrato que {@code CanalNotificacionTurnoWhatsApp}, permitiendo envíos
 * simultáneos por múltiples canales sin acoplar la lógica de notificación a ninguno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanalNotificacionTurnoMail implements CanalNotificacionTurno {

    //region ========== Dependencias o inyecciones ==========

    private final MailService mailService;
    private final ClinicaDomainService clinicaDomainService;

    //endregion

    //region ========== Métodos ==========

    @Override
    public void notificar(TipoNotificacionTurno tipo, Turno turno) {

        log.debug("Notificando por mail: tipo={}, turnoId={}", tipo, turno.getId());

        //Los datos de la clínica van en el pie de todos los mails, y su zona horaria define la hora que ve el paciente
        Clinica clinica = clinicaDomainService.findClinica();
        ZoneId zonaHorariaClinica = ZoneId.of(clinica.getZonaHoraria());

        MensajeMail mensajeMail = switch (tipo) {
            case REGISTRADO -> armarMensajeRegistrado(turno, zonaHorariaClinica);
            case CONFIRMADO -> armarMensajeConfirmado(turno, zonaHorariaClinica);
            case CANCELADO -> armarMensajeCancelado(turno, zonaHorariaClinica);
            case REPROGRAMADO -> armarMensajeReprogramado(turno, zonaHorariaClinica);
            case NO_VALIDADO -> armarMensajeNoValidado(turno, zonaHorariaClinica);
            case RECORDATORIO_CONFIRMACION -> armarMensajeRecordatorioConfirmacion(turno, zonaHorariaClinica);
            case AUSENTE -> armarMensajeAusente(turno, zonaHorariaClinica);
        };

        mailService.enviarMailConPlantilla(turno.getPaciente().getEmail(), mensajeMail.asunto(),
                mensajeMail.contenidoMail(), clinica);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /** Asunto y contenido de un mail de notificación, antes de pasarlo por la plantilla. */
    private record MensajeMail(String asunto, ContenidoMail contenidoMail) {

    }

    private MensajeMail armarMensajeRegistrado(Turno turno, ZoneId zonaHorariaClinica) {

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Turno registrado")
                .colorAcento(PlantillaMailHtml.ACENTO_INFO)
                .titulo("Registramos tu turno")
                .saludo(saludar(turno))
                .parrafos(List.of("Tu turno fue registrado correctamente.",
                        "Confirmalo antes de la fecha límite para asegurar tu lugar."))
                .detalles(detallarTurno(turno, zonaHorariaClinica, "Fecha y hora", true))
                .build();

        return new MensajeMail("Turno registrado", contenidoMail);

    }

    private MensajeMail armarMensajeConfirmado(Turno turno, ZoneId zonaHorariaClinica) {

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Turno confirmado")
                .colorAcento(PlantillaMailHtml.ACENTO_EXITO)
                .titulo("Tu turno está confirmado")
                .saludo(saludar(turno))
                .parrafos(List.of("Tu turno fue confirmado. Te esperamos en la clínica."))
                .detalles(detallarTurno(turno, zonaHorariaClinica, "Fecha y hora", true))
                .build();

        return new MensajeMail("Turno confirmado", contenidoMail);

    }

    private MensajeMail armarMensajeCancelado(Turno turno, ZoneId zonaHorariaClinica) {

        Map<String, String> detalles = detallarTurno(turno, zonaHorariaClinica, "Fecha y hora", false);
        detalles.put("Motivo", describirMotivoCancelacion(turno.getMotivoCancelacion()));

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Turno cancelado")
                .colorAcento(PlantillaMailHtml.ACENTO_ERROR)
                .titulo("Tu turno fue cancelado")
                .saludo(saludar(turno))
                .parrafos(List.of("Tu turno fue cancelado. Podés reservar otro cuando quieras."))
                .detalles(detalles)
                .build();

        return new MensajeMail("Turno cancelado", contenidoMail);

    }

    private MensajeMail armarMensajeReprogramado(Turno turno, ZoneId zonaHorariaClinica) {

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Turno reprogramado")
                .colorAcento(PlantillaMailHtml.ACENTO_INFO)
                .titulo("Reprogramamos tu turno")
                .saludo(saludar(turno))
                .parrafos(List.of("Tu turno fue reprogramado para una nueva fecha.",
                        "Confirmalo antes de la fecha límite para asegurar tu lugar."))
                .detalles(detallarTurno(turno, zonaHorariaClinica, "Nueva fecha y hora", true))
                .build();

        return new MensajeMail("Turno reprogramado", contenidoMail);

    }

    private MensajeMail armarMensajeNoValidado(Turno turno, ZoneId zonaHorariaClinica) {

        Map<String, String> detalles = detallarTurno(turno, zonaHorariaClinica, "Fecha y hora", false);
        detalles.put("Motivo", describirMotivoCancelacion(turno.getMotivoCancelacion()));

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Turno no validado")
                .colorAcento(PlantillaMailHtml.ACENTO_ERROR)
                .titulo("No pudimos validar tu turno")
                .saludo(saludar(turno))
                .parrafos(List.of("Tu turno no pudo ser validado y fue cancelado. Podés reservar otro cuando quieras."))
                .detalles(detalles)
                .build();

        return new MensajeMail("Turno no validado", contenidoMail);

    }

    private MensajeMail armarMensajeRecordatorioConfirmacion(Turno turno, ZoneId zonaHorariaClinica) {

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Recordatorio")
                .colorAcento(PlantillaMailHtml.ACENTO_ALERTA)
                .titulo("Confirmá tu turno")
                .saludo(saludar(turno))
                .parrafos(List.of("Te recordamos que tenés un turno pendiente de confirmación.",
                        "Confirmalo cuanto antes para no perder tu lugar."))
                .detalles(detallarTurno(turno, zonaHorariaClinica, "Fecha y hora", true))
                .build();

        return new MensajeMail("Recordatorio: confirma tu turno", contenidoMail);

    }

    private MensajeMail armarMensajeAusente(Turno turno, ZoneId zonaHorariaClinica) {

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Turno ausente")
                .colorAcento(PlantillaMailHtml.ACENTO_ALERTA)
                .titulo("Registramos tu ausencia")
                .saludo(saludar(turno))
                .parrafos(List.of("Tu turno fue marcado como ausente.",
                        "Si creés que se trata de un error, comunicate con la clínica cuanto antes."))
                .detalles(detallarTurno(turno, zonaHorariaClinica, "Fecha y hora", false))
                .build();

        return new MensajeMail("Turno marcado como ausente", contenidoMail);

    }

    private String saludar(Turno turno) {

        String nombreCompleto = armarNombreCompleto(turno.getPaciente().getNombre(), turno.getPaciente().getApellido());
        return "Hola " + nombreCompleto + ",";

    }

    private Map<String, String> detallarTurno(Turno turno, ZoneId zonaHorariaClinica, String etiquetaFecha,
                                              boolean incluirMedicoYPrestacion) {

        Map<String, String> detalles = new LinkedHashMap<>();

        detalles.put("Código del turno", turno.getCodigo());
        detalles.put(etiquetaFecha, FormatoMensaje.fechaHora(turno.getFechaHoraInicio(), zonaHorariaClinica));

        if (incluirMedicoYPrestacion) {
            detalles.put("Médico", armarNombreCompleto(turno.getMedico().getNombre(), turno.getMedico().getApellido()));
            detalles.put("Prestación", turno.getPrestacion().getNombre());
        }

        return detalles;

    }

    private String armarNombreCompleto(String nombre, String apellido) {

        String nombreCompleto = apellido == null || apellido.isBlank() ? nombre : nombre + " " + apellido;
        return nombreCompleto;

    }

    private String describirMotivoCancelacion(MotivoCancelacion motivoCancelacion) {

        if (motivoCancelacion == null) {
            return "No especificado";
        }

        return switch (motivoCancelacion) {
            case SOLICITUD_DEL_PACIENTE -> "A solicitud del paciente";
            case VALIDACION_RECHAZADA -> "Tu validación fue rechazada";
            case VALIDACION_VENCIDA -> "La validación venció";
            case BAJA_DE_MEDICO -> "El médico ya no atiende";
            case DECISION_ADMINISTRATIVA -> "Decisión administrativa";
        };

    }

    //endregion

}
