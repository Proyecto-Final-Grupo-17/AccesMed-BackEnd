package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Notifications.TurnoNotificacionEvent;
import com.accesmed.backend.Records.Turno.Response.ConfirmTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.FinishTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.StartAtencionTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.StartSalaDeEsperaTurnoResponse;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Utils.FabricaEstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Mappers.TurnoMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link TurnoApp}:
 * verifica los cuatro métodos de transición manual nuevos (confirmTurno, startSalaDeEsperaTurno,
 * startAtencionTurno, finishTurno), especialmente que confirmTurno publica evento CONFIRMADO
 * y los otros tres no publican evento.
 */
@ExtendWith(MockitoExtension.class)
class TurnoAppTest {

    @Mock
    private TurnoDomainService turnoDomainService;
    @Mock
    private HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    @Mock
    private AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    @Mock
    private MedicoDomainService medicoDomainService;
    @Mock
    private PrestacionDomainService prestacionDomainService;
    @Mock
    private HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    @Mock
    private MedicoPrestacionDomainService medicoPrestacionDomainService;
    @Mock
    private IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    @Mock
    private PacienteDomainService pacienteDomainService;
    @Mock
    private ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    @Mock
    private ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    @Mock
    private IndicacionPrestacionTurnoDomainService indicacionPrestacionTurnoDomainService;
    @Mock
    private FabricaEstrategiaCalcularMontoAPagarTurno fabricaEstrategiaCalcularMontoAPagarTurno;
    @Mock
    private TurnoMapper turnoMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TurnoApp turnoApp;

    private UUID turnoId;
    private Turno turnoExistente;

    @BeforeEach
    void setUp() {
        turnoId = UUID.randomUUID();
        turnoExistente = new Turno();
        turnoExistente.setId(turnoId);
        turnoExistente.setCodigo("TURNO_12345678");
        turnoExistente.setFechaHoraInicio(ZonedDateTime.now().plusDays(1));
        turnoExistente.setMontoAPagar(BigDecimal.valueOf(100.00));

        Paciente paciente = new Paciente();
        paciente.setId(UUID.randomUUID());
        paciente.setNombre("Test Paciente");
        paciente.setEmail("test@example.com");
        turnoExistente.setPaciente(paciente);

        Medico medico = new Medico();
        medico.setId(UUID.randomUUID());
        medico.setNombre("Test Medico");
        turnoExistente.setMedico(medico);

        Prestacion prestacion = new Prestacion();
        prestacion.setId(UUID.randomUUID());
        prestacion.setNombre("Test Prestacion");
        turnoExistente.setPrestacion(prestacion);
    }

    // region ========== confirmTurno ==========

    @Test
    void confirmTurno_turnoEnEstadoPENDIENTE_publicaEventoConfirmado() {
        UUID pacienteId = turnoExistente.getPaciente().getId();
        UUID medicoId = turnoExistente.getMedico().getId();
        UUID prestacionId = turnoExistente.getPrestacion().getId();

        when(turnoDomainService.findTurnoById(turnoId)).thenReturn(turnoExistente);
        when(turnoMapper.toConfirmResponse(turnoExistente, EstadoTurno.CONFIRMADO))
                .thenReturn(new ConfirmTurnoResponse(
                        turnoId,
                        "TURNO_12345678",
                        pacienteId,
                        medicoId,
                        prestacionId,
                        turnoExistente.getFechaHoraInicio(),
                        turnoExistente.getMontoAPagar(),
                        "PARTICULAR",
                        EstadoTurno.CONFIRMADO
                ));

        ConfirmTurnoResponse respuesta = turnoApp.confirmTurno(turnoId);

        assertNotNull(respuesta);
        assertEquals(EstadoTurno.CONFIRMADO, respuesta.estadoActual());

        ArgumentCaptor<TurnoNotificacionEvent> eventCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        TurnoNotificacionEvent eventoPublicado = eventCaptor.getValue();
        assertEquals(TipoNotificacionTurno.CONFIRMADO, eventoPublicado.tipo());
        assertEquals(turnoExistente, eventoPublicado.turno());
    }

    // region ========== startSalaDeEsperaTurno ==========

    @Test
    void startSalaDeEsperaTurno_turnoEnEstadoCONFIRMADO_transicionaSinPublicarEvento() {
        UUID pacienteId = turnoExistente.getPaciente().getId();
        UUID medicoId = turnoExistente.getMedico().getId();
        UUID prestacionId = turnoExistente.getPrestacion().getId();

        when(turnoDomainService.findTurnoById(turnoId)).thenReturn(turnoExistente);
        when(turnoMapper.toStartSalaDeEsperaResponse(turnoExistente, EstadoTurno.EN_SALA_DE_ESPERA))
                .thenReturn(new StartSalaDeEsperaTurnoResponse(
                        turnoId,
                        "TURNO_12345678",
                        pacienteId,
                        medicoId,
                        prestacionId,
                        turnoExistente.getFechaHoraInicio(),
                        turnoExistente.getMontoAPagar(),
                        "PARTICULAR",
                        EstadoTurno.EN_SALA_DE_ESPERA
                ));

        StartSalaDeEsperaTurnoResponse respuesta = turnoApp.startSalaDeEsperaTurno(turnoId);

        assertNotNull(respuesta);
        assertEquals(EstadoTurno.EN_SALA_DE_ESPERA, respuesta.estadoActual());

        verify(historicoEstadoTurnoDomainService).transitionConfirmadoToEnSalaDeEsperaTurno(turnoExistente);
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    // region ========== startAtencionTurno ==========

    @Test
    void startAtencionTurno_turnoEnEstadoEN_SALA_DE_ESPERA_transicionaSinPublicarEvento() {
        UUID pacienteId = turnoExistente.getPaciente().getId();
        UUID medicoId = turnoExistente.getMedico().getId();
        UUID prestacionId = turnoExistente.getPrestacion().getId();

        when(turnoDomainService.findTurnoById(turnoId)).thenReturn(turnoExistente);
        when(turnoMapper.toStartAtencionResponse(turnoExistente, EstadoTurno.EN_CURSO))
                .thenReturn(new StartAtencionTurnoResponse(
                        turnoId,
                        "TURNO_12345678",
                        pacienteId,
                        medicoId,
                        prestacionId,
                        turnoExistente.getFechaHoraInicio(),
                        turnoExistente.getMontoAPagar(),
                        "PARTICULAR",
                        EstadoTurno.EN_CURSO
                ));

        StartAtencionTurnoResponse respuesta = turnoApp.startAtencionTurno(turnoId);

        assertNotNull(respuesta);
        assertEquals(EstadoTurno.EN_CURSO, respuesta.estadoActual());

        verify(historicoEstadoTurnoDomainService).transitionEnSalaDeEsperaToEnCursoTurno(turnoExistente);
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    // region ========== finishTurno ==========

    @Test
    void finishTurno_turnoEnEstadoEN_CURSO_transicionaSinPublicarEvento() {
        UUID pacienteId = turnoExistente.getPaciente().getId();
        UUID medicoId = turnoExistente.getMedico().getId();
        UUID prestacionId = turnoExistente.getPrestacion().getId();

        when(turnoDomainService.findTurnoById(turnoId)).thenReturn(turnoExistente);
        when(turnoMapper.toFinishResponse(turnoExistente, EstadoTurno.FINALIZADO))
                .thenReturn(new FinishTurnoResponse(
                        turnoId,
                        "TURNO_12345678",
                        pacienteId,
                        medicoId,
                        prestacionId,
                        turnoExistente.getFechaHoraInicio(),
                        turnoExistente.getMontoAPagar(),
                        "PARTICULAR",
                        EstadoTurno.FINALIZADO
                ));

        FinishTurnoResponse respuesta = turnoApp.finishTurno(turnoId);

        assertNotNull(respuesta);
        assertEquals(EstadoTurno.FINALIZADO, respuesta.estadoActual());

        verify(historicoEstadoTurnoDomainService).transitionEnCursoToFinalizadoTurno(turnoExistente);
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

}
