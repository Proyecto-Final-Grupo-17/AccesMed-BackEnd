package com.accesmed.backend.Notifications.Canales;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.Utils.MailService;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml.ContenidoMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link CanalNotificacionTurnoMail}:
 * verifica que para cada tipo de notificación se llama a mailService.enviarMailConPlantilla
 * con el email del paciente, el asunto esperado y los datos de la clínica para el pie.
 */
@ExtendWith(MockitoExtension.class)
class CanalNotificacionTurnoMailTest {

    @Mock
    private MailService mailService;

    @Mock
    private ClinicaDomainService clinicaDomainService;

    @InjectMocks
    private CanalNotificacionTurnoMail canalNotificacionTurnoMail;

    private Turno turno;
    private Clinica clinica;
    private static final String EMAIL_PACIENTE = "paciente@example.com";

    @BeforeEach
    void setUp() {
        turno = new Turno();
        turno.setId(UUID.randomUUID());
        turno.setCodigo("TURNO_12345678");

        Paciente paciente = new Paciente();
        paciente.setNombre("Juan");
        paciente.setApellido("Perez");
        paciente.setEmail(EMAIL_PACIENTE);
        turno.setPaciente(paciente);

        Medico medico = new Medico();
        medico.setNombre("Ana");
        medico.setApellido("Rodriguez");
        turno.setMedico(medico);

        Prestacion prestacion = new Prestacion();
        prestacion.setNombre("Consulta General");
        turno.setPrestacion(prestacion);

        turno.setFechaHoraInicio(ZonedDateTime.now().plusDays(1));

        clinica = new Clinica();
        clinica.setNombre("Clínica AccesMed");
        clinica.setUbicacion("Av. San Martín 123, Mendoza");
        clinica.setZonaHoraria("America/Argentina/Buenos_Aires");
        when(clinicaDomainService.findClinica()).thenReturn(clinica);
    }

    @Test
    void notificar_tipoREGISTRADO_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.REGISTRADO, turno);

        verifyMailEnviado("Turno registrado");
    }

    @Test
    void notificar_tipoCONFIRMADO_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.CONFIRMADO, turno);

        verifyMailEnviado("Turno confirmado");
    }

    @Test
    void notificar_tipoCANCELADO_enviaMailAlPaciente() {
        turno.setMotivoCancelacion(MotivoCancelacion.SOLICITUD_DEL_PACIENTE);
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.CANCELADO, turno);

        verifyMailEnviado("Turno cancelado");
    }

    @Test
    void notificar_tipoREPROGRAMADO_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.REPROGRAMADO, turno);

        verifyMailEnviado("Turno reprogramado");
    }

    @Test
    void notificar_tipoNO_VALIDADO_enviaMailAlPaciente() {
        turno.setMotivoCancelacion(MotivoCancelacion.VALIDACION_RECHAZADA);
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.NO_VALIDADO, turno);

        verifyMailEnviado("Turno no validado");
    }

    @Test
    void notificar_tipoRECORDATORIO_CONFIRMACION_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.RECORDATORIO_CONFIRMACION, turno);

        verifyMailEnviado("Recordatorio: confirma tu turno");
    }

    @Test
    void notificar_tipoAUSENTE_enviaMailAlPaciente() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.AUSENTE, turno);

        verifyMailEnviado("Turno marcado como ausente");
    }

    @Test
    void notificar_turnoCancelado_incluyeElMotivoYLaHoraEnLaZonaDeLaClinica() {
        turno.setMotivoCancelacion(MotivoCancelacion.VALIDACION_VENCIDA);
        turno.setFechaHoraInicio(ZonedDateTime.of(2026, 11, 11, 17, 0, 0, 0, ZoneId.of("UTC")));

        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.CANCELADO, turno);

        ArgumentCaptor<ContenidoMail> contenidoCaptor = ArgumentCaptor.forClass(ContenidoMail.class);
        verify(mailService).enviarMailConPlantilla(eq(EMAIL_PACIENTE), eq("Turno cancelado"),
                contenidoCaptor.capture(), eq(clinica));

        ContenidoMail contenidoMail = contenidoCaptor.getValue();
        assertEquals("La validación venció", contenidoMail.detalles().get("Motivo"));
        //17:00 UTC son las 14:00 en Buenos Aires: el paciente tiene que ver la hora de la clínica
        assertEquals("11/11/2026 14:00", contenidoMail.detalles().get("Fecha y hora"));
        assertEquals("Hola Juan Perez,", contenidoMail.saludo());
    }

    @Test
    void notificar_turnoConfirmado_incluyeMedicoYPrestacion() {
        canalNotificacionTurnoMail.notificar(TipoNotificacionTurno.CONFIRMADO, turno);

        ArgumentCaptor<ContenidoMail> contenidoCaptor = ArgumentCaptor.forClass(ContenidoMail.class);
        verify(mailService).enviarMailConPlantilla(eq(EMAIL_PACIENTE), eq("Turno confirmado"),
                contenidoCaptor.capture(), eq(clinica));

        ContenidoMail contenidoMail = contenidoCaptor.getValue();
        assertEquals("Ana Rodriguez", contenidoMail.detalles().get("Médico"));
        assertEquals("Consulta General", contenidoMail.detalles().get("Prestación"));
        assertTrue(contenidoMail.detalles().containsKey("Código del turno"));
    }

    private void verifyMailEnviado(String asuntoEsperado) {
        verify(mailService).enviarMailConPlantilla(eq(EMAIL_PACIENTE), eq(asuntoEsperado),
                any(ContenidoMail.class), eq(clinica));
    }

}
