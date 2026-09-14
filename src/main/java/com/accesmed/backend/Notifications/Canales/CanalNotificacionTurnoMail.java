package com.accesmed.backend.Notifications.Canales;

import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Services.Utils.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Adaptador de {@link CanalNotificacionTurno} que arma el mensaje según el
 * {@link TipoNotificacionTurno} y lo despacha por correo electrónico. Implementa
 * el mismo contrato que {@code CanalNotificacionTurnoWhatsApp}, permitiendo envíos
 * simultáneos por múltiples canales sin acoplar la lógica de notificación a ninguno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanalNotificacionTurnoMail implements CanalNotificacionTurno {

    private final MailService mailService;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void notificar(TipoNotificacionTurno tipo, Turno turno) {
        log.debug("Notificando por mail: tipo={}, turnoId={}", tipo, turno.getId());

        String asunto;
        String cuerpo;

        switch (tipo) {
            case REGISTRADO:
                asunto = "Turno registrado";
                cuerpo = armarCuerpoRegistrado(turno);
                break;

            case CONFIRMADO:
                asunto = "Turno confirmado";
                cuerpo = armarCuerpoConfirmado(turno);
                break;

            case CANCELADO:
                asunto = "Turno cancelado";
                cuerpo = armarCuerpoCancelado(turno);
                break;

            case REPROGRAMADO:
                asunto = "Turno reprogramado";
                cuerpo = armarCuerpoReprogramado(turno);
                break;

            case NO_VALIDADO:
                asunto = "Turno no validado";
                cuerpo = armarCuerpoNoValidado(turno);
                break;

            case RECORDATORIO_CONFIRMACION:
                asunto = "Recordatorio: confirma tu turno";
                cuerpo = armarCuerpoRecordatorioConfirmacion(turno);
                break;

            case AUSENTE:
                asunto = "Turno marcado como ausente";
                cuerpo = armarCuerpoAusente(turno);
                break;

            default:
                log.warn("Tipo de notificación desconocido: {}", tipo);
                return;
        }

        mailService.enviarMail(turno.getPaciente().getEmail(), asunto, cuerpo);
    }

    private String armarCuerpoRegistrado(Turno turno) {
        return String.format(
                "Estimado/a %s,\n\n" +
                "Tu turno ha sido registrado exitosamente.\n\n" +
                "Código del turno: %s\n" +
                "Fecha y hora: %s\n" +
                "Médico: %s\n" +
                "Prestación: %s\n\n" +
                "Por favor confirma tu turno antes de la fecha límite.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA),
                turno.getMedico().getNombre(),
                turno.getPrestacion().getNombre()
        );
    }

    private String armarCuerpoConfirmado(Turno turno) {
        return String.format(
                "Estimado/a %s,\n\n" +
                "Tu turno ha sido confirmado.\n\n" +
                "Código del turno: %s\n" +
                "Fecha y hora: %s\n" +
                "Médico: %s\n" +
                "Prestación: %s\n\n" +
                "Te esperamos en la clínica.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA),
                turno.getMedico().getNombre(),
                turno.getPrestacion().getNombre()
        );
    }

    private String armarCuerpoCancelado(Turno turno) {
        String motivo = turno.getMotivoCancelacion() != null ?
                switch (turno.getMotivoCancelacion()) {
                    case SOLICITUD_DEL_PACIENTE -> "a solicitud del paciente";
                    case VALIDACION_RECHAZADA -> "tu validación fue rechazada";
                    case VALIDACION_VENCIDA -> "la validación venció";
                    case BAJA_DE_MEDICO -> "el médico ya no atiende";
                    case DECISION_ADMINISTRATIVA -> "decisión administrativa";
                } : "motivo no especificado";

        return String.format(
                "Estimado/a %s,\n\n" +
                "Tu turno ha sido cancelado: %s.\n\n" +
                "Código del turno: %s\n" +
                "Fecha y hora: %s\n\n" +
                "Para reservar otro turno, ingresa a nuestra plataforma.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                motivo,
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA)
        );
    }

    private String armarCuerpoReprogramado(Turno turno) {
        return String.format(
                "Estimado/a %s,\n\n" +
                "Tu turno ha sido reprogramado.\n\n" +
                "Código del turno: %s\n" +
                "Nueva fecha y hora: %s\n" +
                "Médico: %s\n" +
                "Prestación: %s\n\n" +
                "Por favor confirma tu turno antes de la fecha límite.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA),
                turno.getMedico().getNombre(),
                turno.getPrestacion().getNombre()
        );
    }

    private String armarCuerpoNoValidado(Turno turno) {
        String motivo = turno.getMotivoCancelacion() != null ?
                switch (turno.getMotivoCancelacion()) {
                    case VALIDACION_RECHAZADA -> "tu validación fue rechazada";
                    case VALIDACION_VENCIDA -> "la validación venció";
                    default -> "motivo no especificado";
                } : "motivo no especificado";

        return String.format(
                "Estimado/a %s,\n\n" +
                "Tu turno no pudo ser validado: %s.\n\n" +
                "Código del turno: %s\n" +
                "Fecha y hora: %s\n\n" +
                "Para reservar otro turno, ingresa a nuestra plataforma.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                motivo,
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA)
        );
    }

    private String armarCuerpoRecordatorioConfirmacion(Turno turno) {
        return String.format(
                "Estimado/a %s,\n\n" +
                "Te recordamos que tienes un turno pendiente de confirmación.\n\n" +
                "Código del turno: %s\n" +
                "Fecha y hora: %s\n" +
                "Médico: %s\n" +
                "Prestación: %s\n\n" +
                "Por favor confirma tu turno cuanto antes.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA),
                turno.getMedico().getNombre(),
                turno.getPrestacion().getNombre()
        );
    }

    private String armarCuerpoAusente(Turno turno) {
        return String.format(
                "Estimado/a %s,\n\n" +
                "Tu turno ha sido marcado como ausente.\n\n" +
                "Código del turno: %s\n" +
                "Fecha y hora: %s\n\n" +
                "Si consideras que esto es un error, contactanos cuanto antes.\n\n" +
                "Saludos,\nAccesMed",
                turno.getPaciente().getNombre(),
                turno.getCodigo(),
                turno.getFechaHoraInicio().format(FORMATO_FECHA)
        );
    }

}
