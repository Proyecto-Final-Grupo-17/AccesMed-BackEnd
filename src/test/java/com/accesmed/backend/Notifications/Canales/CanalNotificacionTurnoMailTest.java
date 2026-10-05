package com.accesmed.backend.Notifications.Canales;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Services.Utils.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de {@link CanalNotificacionTurnoMail}:
 * verifica que para cada tipo de notificación se llama a mailService.enviarMail
 * con el email del paciente y un asunto/cuerpo apropiado.
 */
@ExtendWith(MockitoExtension.class)
class CanalNotificacionTurnoMailTest {

    @Mock
    private MailService mailService;

    @InjectMocks
    private CanalNotificacionTurnoMail canalNotificacionTurnoMail;

    private Turno turno;
    private static final String EMAIL_PACIENTE = "paciente@example.com";

    @BeforeEach
    void setUp() {
        turno = new Turno();
        turno.setId(UUID.randomUUID());
        turno.setCodigo("TURNO_12345678");

        Paciente paciente = new Paciente();
        paciente.setNombre("Juan Perez");
        paciente.setEmail(EMAIL_PACIENTE);
        turno.setPaciente(paciente);

        Medico medico = new Medico();
        medico.setNombre("Dr. Rodriguez");
        turno.setMedico(medico);

        Prestacion prestacion = new Prestacion();
        prestacion.setNombre("Consulta General");
        turno.setPrestacion(prestacion);

        turno.setFechaHoraInicio(ZonedDateTime.now().plusDays(1));
    }

    @Test
    void notificar_tipoREGISTRADO_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.REGISTRADO, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Turno registrado"),
                anyString()
        );
    }

    @Test
    void notificar_tipoCONFIRMADO_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.CONFIRMADO, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Turno confirmado"),
                anyString()
        );
    }

    @Test
    void notificar_tipoCANCELADO_enviaMailAlPaciente() {
        turno.setMotivoCancelacion(MotivoCancelacion.SOLICITUD_DEL_PACIENTE);
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.CANCELADO, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Turno cancelado"),
                anyString()
        );
    }

    @Test
    void notificar_tipoREPROGRAMADO_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.REPROGRAMADO, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Turno reprogramado"),
                anyString()
        );
    }

    @Test
    void notificar_tipoNO_VALIDADO_enviaMailAlPaciente() {
        turno.setMotivoCancelacion(MotivoCancelacion.VALIDACION_RECHAZADA);
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.NO_VALIDADO, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Turno no validado"),
                anyString()
        );
    }

    @Test
    void notificar_tipoRECORDATORIO_CONFIRMACION_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.RECORDATORIO_CONFIRMACION, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Recordatorio: confirma tu turno"),
                anyString()
        );
    }

    @Test
    void notificar_tipoAUSENTE_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.AUSENTE, turno);

        verify(mailService).enviarMail(
                eq(EMAIL_PACIENTE),
                eq("Turno marcado como ausente"),
                anyString()
        );
    }

}
