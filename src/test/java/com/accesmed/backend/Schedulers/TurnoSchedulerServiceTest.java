package com.accesmed.backend.Schedulers;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Notifications.TurnoNotificacionEvent;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de {@link TurnoSchedulerService}:
 * verifica que cada método de transición automática ejecuta la secuencia correcta
 * de operaciones y publica el evento correspondiente.
 */
@ExtendWith(MockitoExtension.class)
class TurnoSchedulerServiceTest {

    @Mock
    private TurnoDomainService turnoDomainService;
    @Mock
    private HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    @Mock
    private AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TurnoSchedulerService turnoSchedulerService;

    private Turno turno;

    @BeforeEach
    void setUp() {
        turno = new Turno();
        turno.setId(UUID.randomUUID());

        Paciente paciente = new Paciente();
        paciente.setNombre("Test Paciente");
        paciente.setEmail("test@example.com");
        turno.setPaciente(paciente);

        Medico medico = new Medico();
        medico.setNombre("Test Medico");
        turno.setMedico(medico);

        Prestacion prestacion = new Prestacion();
        prestacion.setNombre("Test Prestacion");
        turno.setPrestacion(prestacion);
    }

    // region ========== confirmarTurno ==========

    @Test
    void confirmarTurno_ejecutaTransicionYPublicaEventoConfirmado() {
        turnoSchedulerService.confirmarTurno(turno);

        verify(historicoEstadoTurnoDomainService).transitionPendienteToConfirmadoTurno(turno);

        ArgumentCaptor<TurnoNotificacionEvent> eventCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        TurnoNotificacionEvent eventoPublicado = eventCaptor.getValue();
        assertEquals(TipoNotificacionTurno.CONFIRMADO, eventoPublicado.tipo());
        assertEquals(turno, eventoPublicado.turno());
    }

    // region ========== vencerValidacionTurno ==========

    @Test
    void vencerValidacionTurno_liberaSlot_guarda_transicionaYPublicaEventoNoValidado() {
        AgendaHorariosDia agendaHorarios = new AgendaHorariosDia();
        turno.setAgendaHorarios(agendaHorarios);

        turnoSchedulerService.vencerValidacionTurno(turno);

        verify(agendaHorariosDiaDomainService).releaseAgendaHorario(agendaHorarios);
        assertEquals(MotivoCancelacion.VALIDACION_VENCIDA, turno.getMotivoCancelacion());
        verify(turnoDomainService).saveTurno(turno);
        verify(historicoEstadoTurnoDomainService).transitionEsperaValidacionToCanceladoTurno(turno);

        ArgumentCaptor<TurnoNotificacionEvent> eventCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        TurnoNotificacionEvent eventoPublicado = eventCaptor.getValue();
        assertEquals(TipoNotificacionTurno.NO_VALIDADO, eventoPublicado.tipo());
        assertEquals(turno, eventoPublicado.turno());
    }

    // region ========== marcarAusenteTurno ==========

    @Test
    void marcarAusenteTurno_transicionaYPublicaEventoAusente() {
        turnoSchedulerService.marcarAusenteTurno(turno);

        verify(historicoEstadoTurnoDomainService).transitionToAusenteTurno(turno);

        ArgumentCaptor<TurnoNotificacionEvent> eventCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        TurnoNotificacionEvent eventoPublicado = eventCaptor.getValue();
        assertEquals(TipoNotificacionTurno.AUSENTE, eventoPublicado.tipo());
        assertEquals(turno, eventoPublicado.turno());
    }

    // region ========== recordarConfirmacionTurno ==========

    @Test
    void recordarConfirmacionTurno_soloPublicaEventoSinTransicionar() {
        turnoSchedulerService.recordarConfirmacionTurno(turno);

        verify(historicoEstadoTurnoDomainService, org.mockito.Mockito.never()).transitionPendienteToConfirmadoTurno(turno);

        ArgumentCaptor<TurnoNotificacionEvent> eventCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        TurnoNotificacionEvent eventoPublicado = eventCaptor.getValue();
        assertEquals(TipoNotificacionTurno.RECORDATORIO_CONFIRMACION, eventoPublicado.tipo());
        assertEquals(turno, eventoPublicado.turno());
    }

}
