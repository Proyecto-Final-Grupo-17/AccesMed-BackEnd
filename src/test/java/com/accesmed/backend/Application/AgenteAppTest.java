package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoCobertura;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Notifications.TurnoNotificacionEvent;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.Agente.Request.AccionTurnoAgente;
import com.accesmed.backend.Records.Agente.Request.CreateTurnoAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Request.ReprogramTurnoAgenteRequest;
import com.accesmed.backend.Records.Agente.Response.ConfirmTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreateTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ReprogramTurnoAgenteResponse;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.AgenteMapper;
import com.accesmed.backend.Services.Mappers.PacienteMapper;
import com.accesmed.backend.Services.QueryServices.AgendaHorariosDiaQueryService;
import com.accesmed.backend.Services.Utils.EstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.FabricaEstrategiaCalcularMontoAPagarTurno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link AgenteApp}: identidad por teléfono, scope por paciente
 * ({@code RECURSO_AJENO}), plazos y los casos felices de crear, cancelar, reprogramar y confirmar
 * (la orquestación copiada de {@code TurnoApp}).
 */
@ExtendWith(MockitoExtension.class)
class AgenteAppTest {

    private static final String TELEFONO = "+5492617742682";

    @Mock
    private PacienteDomainService pacienteDomainService;
    @Mock
    private ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    @Mock
    private ObraSocialDomainService obraSocialDomainService;
    @Mock
    private PlanDomainService planDomainService;
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
    private IndicacionPrestacionTurnoDomainService indicacionPrestacionTurnoDomainService;
    @Mock
    private ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    @Mock
    private EspecialidadDomainService especialidadDomainService;
    @Mock
    private ClinicaDomainService clinicaDomainService;
    @Mock
    private AgendaHorariosDiaQueryService agendaHorariosDiaQueryService;
    @Mock
    private FabricaEstrategiaCalcularMontoAPagarTurno fabricaEstrategiaCalcularMontoAPagarTurno;
    @Mock
    private AgenteMapper agenteMapper;
    @Mock
    private PacienteMapper pacienteMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private AgenteApp agenteApp;

    private Paciente paciente;
    private Medico medico;
    private Prestacion prestacion;

    @BeforeEach
    void setUp() {
        paciente = new Paciente();
        paciente.setId(UUID.randomUUID());

        medico = new Medico();
        medico.setId(UUID.randomUUID());
        medico.setNombre("Ana");
        medico.setApellido("Gómez");

        prestacion = new Prestacion();
        prestacion.setId(UUID.randomUUID());
        prestacion.setNombre("Consulta clínica");
        prestacion.setTiempoToleranciaSolicitud(Duration.ofHours(1));
        prestacion.setTiempoToleranciaValidacion(Duration.ofHours(2));
        prestacion.setTiempoToleranciaReprogramacion(Duration.ofHours(3));
        prestacion.setTiempoToleranciaConfirmacion(Duration.ofHours(4));
        prestacion.setTiempoToleranciaCancelacion(Duration.ofHours(5));
        prestacion.setTiempoToleranciaAnuncio(Duration.ofMinutes(30));
        prestacion.setTiempoRecordatorioConfirmacion(Duration.ofHours(6));
    }

    //region ========== identidad por teléfono ==========

    @Test
    void getPaciente_telefonoDesconocido_propagaPacienteNoEncontrado() {
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO))
                .thenThrow(new RecursoNoEncontradoException(PacienteDomainService.class, "PACIENTE_NO_ENCONTRADO", "no existe"));

        RecursoNoEncontradoException excepcion = assertThrows(RecursoNoEncontradoException.class,
                () -> agenteApp.getPaciente(TELEFONO));

        assertEquals("PACIENTE_NO_ENCONTRADO", excepcion.getCodigo());
    }

    @Test
    void cancelTurno_telefonoDesconocido_noTocaElTurno() {
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO))
                .thenThrow(new RecursoNoEncontradoException(PacienteDomainService.class, "PACIENTE_NO_ENCONTRADO", "no existe"));

        assertThrows(RecursoNoEncontradoException.class, () -> agenteApp.cancelTurno(TELEFONO, "TURNO_A1B2C3D4"));

        verify(turnoDomainService, never()).findTurnoByCodigo(any());
    }

    //endregion

    //region ========== scope por paciente ==========

    @Test
    void cancelTurno_turnoDeOtroPaciente_lanzaRecursoAjenoYNoCancela() {
        Turno turnoAjeno = turnoVivo();
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo("TURNO_AJENO")).thenReturn(turnoAjeno);
        doThrow(new ReglaNegocioException(TurnoDomainService.class, "RECURSO_AJENO", "ajeno"))
                .when(turnoDomainService).validateTurnoPerteneceAPaciente(turnoAjeno, paciente);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.cancelTurno(TELEFONO, "TURNO_AJENO"));

        assertEquals("RECURSO_AJENO", excepcion.getCodigo());
        verify(agendaHorariosDiaDomainService, never()).releaseAgendaHorario(any());
        verify(historicoEstadoTurnoDomainService, never()).transitionToCanceladoTurno(any());
    }

    @Test
    void confirmTurno_turnoDeOtroPaciente_lanzaRecursoAjenoYNoConfirma() {
        Turno turnoAjeno = turnoVivo();
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo("TURNO_AJENO")).thenReturn(turnoAjeno);
        doThrow(new ReglaNegocioException(TurnoDomainService.class, "RECURSO_AJENO", "ajeno"))
                .when(turnoDomainService).validateTurnoPerteneceAPaciente(turnoAjeno, paciente);

        assertThrows(ReglaNegocioException.class, () -> agenteApp.confirmTurno(TELEFONO, "TURNO_AJENO"));

        verify(historicoEstadoTurnoDomainService, never()).transitionPendienteToConfirmadoTurno(any());
    }

    @Test
    void reprogramTurno_turnoDeOtroPaciente_lanzaRecursoAjenoYNoReprograma() {
        Turno turnoAjeno = turnoVivo();
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo("TURNO_AJENO")).thenReturn(turnoAjeno);
        doThrow(new ReglaNegocioException(TurnoDomainService.class, "RECURSO_AJENO", "ajeno"))
                .when(turnoDomainService).validateTurnoPerteneceAPaciente(turnoAjeno, paciente);

        assertThrows(ReglaNegocioException.class, () -> agenteApp.reprogramTurno(TELEFONO, "TURNO_AJENO",
                new ReprogramTurnoAgenteRequest(UUID.randomUUID())));

        verify(historicoEstadoTurnoDomainService, never()).transitionToReprogramadoTurno(any());
    }

    //endregion

    //region ========== cancelar ==========

    @Test
    void cancelTurno_fueraDePlazo_lanzaReglaNegocioYNoLiberaElSlot() {
        Turno turno = turnoVivo();
        turno.setFechaLimiteCancelacion(ZonedDateTime.now().minusMinutes(1));
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo(turno.getCodigo())).thenReturn(turno);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.cancelTurno(TELEFONO, turno.getCodigo()));

        assertEquals("TURNO_FUERA_DE_PLAZO_CANCELACION", excepcion.getCodigo());
        verify(agendaHorariosDiaDomainService, never()).releaseAgendaHorario(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelTurno_casoFeliz_liberaSlotCancelaConMotivoDelPacienteYPublicaEvento() {
        Turno turno = turnoVivo();
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo(turno.getCodigo())).thenReturn(turno);

        agenteApp.cancelTurno(TELEFONO, turno.getCodigo());

        verify(turnoDomainService).validateTurnoPerteneceAPaciente(turno, paciente);
        verify(agendaHorariosDiaDomainService).releaseAgendaHorario(turno.getAgendaHorarios());
        verify(historicoEstadoTurnoDomainService).transitionToCanceladoTurno(turno);
        assertEquals(MotivoCancelacion.SOLICITUD_DEL_PACIENTE, turno.getMotivoCancelacion());

        ArgumentCaptor<TurnoNotificacionEvent> eventoCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventoCaptor.capture());
        assertEquals(TipoNotificacionTurno.CANCELADO, eventoCaptor.getValue().tipo());
    }

    //endregion

    //region ========== confirmar ==========

    @Test
    void confirmTurno_fueraDePlazo_lanzaReglaNegocio() {
        Turno turno = turnoVivo();
        turno.setFechaLimiteConfirmacion(ZonedDateTime.now().minusMinutes(1));
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo(turno.getCodigo())).thenReturn(turno);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.confirmTurno(TELEFONO, turno.getCodigo()));

        assertEquals("TURNO_FUERA_DE_PLAZO_CONFIRMACION", excepcion.getCodigo());
        verify(historicoEstadoTurnoDomainService, never()).transitionPendienteToConfirmadoTurno(any());
    }

    @Test
    void confirmTurno_casoFeliz_transicionaYPublicaEventoConfirmado() {
        Turno turno = turnoVivo();
        ConfirmTurnoAgenteResponse respuestaEsperada = new ConfirmTurnoAgenteResponse(
                turno.getCodigo(), turno.getFechaHoraInicio(), "Consulta clínica", "Ana", "Gómez", EstadoTurno.CONFIRMADO);
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo(turno.getCodigo())).thenReturn(turno);
        when(agenteMapper.toConfirmTurnoResponse(turno, EstadoTurno.CONFIRMADO)).thenReturn(respuestaEsperada);

        ConfirmTurnoAgenteResponse respuesta = agenteApp.confirmTurno(TELEFONO, turno.getCodigo());

        assertEquals(EstadoTurno.CONFIRMADO, respuesta.estadoActual());
        verify(historicoEstadoTurnoDomainService).transitionPendienteToConfirmadoTurno(turno);
        ArgumentCaptor<TurnoNotificacionEvent> eventoCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventoCaptor.capture());
        assertEquals(TipoNotificacionTurno.CONFIRMADO, eventoCaptor.getValue().tipo());
    }

    //endregion

    //region ========== crear ==========

    @Test
    void createTurno_sinCobertura_creaTurnoParticularDerivandoMedicoYPrestacionDelSlot() {
        AgendaHorariosDia slot = slotLibre(medico, prestacion);
        MedicoPrestacion medicoPrestacion = new MedicoPrestacion();
        medicoPrestacion.setPrecioParticular(BigDecimal.valueOf(8000));
        CreateTurnoAgenteResponse respuestaEsperada = mock(CreateTurnoAgenteResponse.class);

        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(agendaHorariosDiaDomainService.findAgendaHorarioActivoById(slot.getId())).thenReturn(slot);
        when(medicoDomainService.findMedicoActivoById(medico.getId())).thenReturn(medico);
        when(prestacionDomainService.findPrestacionActivaById(prestacion.getId())).thenReturn(prestacion);
        when(historicoEstadoPrestacionDomainService.getEstadoVigente(prestacion.getId())).thenReturn(EstadoPrestacion.PUBLICADA);
        when(agendaHorariosDiaDomainService.findAgendaHorarioDisponible(slot.getId(), medico.getId(), prestacion.getId()))
                .thenReturn(slot);
        when(medicoPrestacionDomainService.findVigenteEnFecha(medico.getId(), prestacion.getId(), slot.getFecha()))
                .thenReturn(medicoPrestacion);
        when(indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(prestacion.getId()))
                .thenReturn(List.of());
        when(obraSocialPacienteDomainService.findCoberturasActivasByPaciente(paciente.getId())).thenReturn(List.of());
        when(turnoDomainService.saveTurno(any(Turno.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(agenteMapper.toCreateTurnoResponse(any(Turno.class), eq(EstadoTurno.PENDIENTE))).thenReturn(respuestaEsperada);

        CreateTurnoAgenteResponse respuesta = agenteApp.createTurno(TELEFONO, new CreateTurnoAgenteRequest(slot.getId()));

        assertEquals(respuestaEsperada, respuesta);
        ArgumentCaptor<Turno> turnoCaptor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoDomainService).saveTurno(turnoCaptor.capture());
        Turno turnoGuardado = turnoCaptor.getValue();
        assertEquals(paciente, turnoGuardado.getPaciente());
        assertEquals(medico, turnoGuardado.getMedico());
        assertEquals(prestacion, turnoGuardado.getPrestacion());
        assertEquals(TipoCobertura.PARTICULAR, turnoGuardado.getTipoCobertura());
        assertEquals(BigDecimal.valueOf(8000), turnoGuardado.getMontoAPagar());
        assertTrue(turnoGuardado.getCodigo().startsWith("TURNO_"));
        verify(agendaHorariosDiaDomainService).occupyAgendaHorario(slot);
        verify(historicoEstadoTurnoDomainService).setEstadoInicialTurno(turnoGuardado, EstadoTurno.PENDIENTE);
        ArgumentCaptor<TurnoNotificacionEvent> eventoCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventoCaptor.capture());
        assertEquals(TipoNotificacionTurno.REGISTRADO, eventoCaptor.getValue().tipo());
    }

    @Test
    void createTurno_pacienteConPlanQueCubreLaPrestacion_usaLaCoberturaDeObraSocial() {
        AgendaHorariosDia slot = slotLibre(medico, prestacion);
        MedicoPrestacion medicoPrestacion = new MedicoPrestacion();
        medicoPrestacion.setPrecioParticular(BigDecimal.valueOf(8000));
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setObraSocial(new ObraSocial());
        ObraSocialPaciente cobertura = new ObraSocialPaciente("123", paciente, plan);
        ObraSocialPlanPrestacion coberturaPlan = new ObraSocialPlanPrestacion();
        EstrategiaCalcularMontoAPagarTurno estrategia = mock(EstrategiaCalcularMontoAPagarTurno.class);

        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(agendaHorariosDiaDomainService.findAgendaHorarioActivoById(slot.getId())).thenReturn(slot);
        when(medicoDomainService.findMedicoActivoById(medico.getId())).thenReturn(medico);
        when(prestacionDomainService.findPrestacionActivaById(prestacion.getId())).thenReturn(prestacion);
        when(historicoEstadoPrestacionDomainService.getEstadoVigente(prestacion.getId())).thenReturn(EstadoPrestacion.PUBLICADA);
        when(agendaHorariosDiaDomainService.findAgendaHorarioDisponible(slot.getId(), medico.getId(), prestacion.getId()))
                .thenReturn(slot);
        when(medicoPrestacionDomainService.findVigenteEnFecha(medico.getId(), prestacion.getId(), slot.getFecha()))
                .thenReturn(medicoPrestacion);
        when(indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(prestacion.getId()))
                .thenReturn(List.of());
        when(obraSocialPacienteDomainService.findCoberturasActivasByPaciente(paciente.getId())).thenReturn(List.of(cobertura));
        when(obraSocialPlanPrestacionDomainService.existsCoberturaActivaByPlanAndPrestacion(plan.getId(), prestacion.getId()))
                .thenReturn(true);
        when(obraSocialPlanPrestacionDomainService.findCoberturaByPlanAndPrestacion(plan.getId(), prestacion.getId()))
                .thenReturn(coberturaPlan);
        when(fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(any())).thenReturn(estrategia);
        when(estrategia.calcularMonto(any())).thenReturn(BigDecimal.valueOf(1500));
        when(turnoDomainService.saveTurno(any(Turno.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        agenteApp.createTurno(TELEFONO, new CreateTurnoAgenteRequest(slot.getId()));

        ArgumentCaptor<Turno> turnoCaptor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoDomainService).saveTurno(turnoCaptor.capture());
        assertEquals(TipoCobertura.OBRA_SOCIAL, turnoCaptor.getValue().getTipoCobertura());
        assertEquals(cobertura, turnoCaptor.getValue().getObraSocialPaciente());
        assertEquals(BigDecimal.valueOf(1500), turnoCaptor.getValue().getMontoAPagar());
    }

    @Test
    void createTurno_prestacionNoPublicada_lanzaReglaNegocioYNoOcupaElSlot() {
        AgendaHorariosDia slot = slotLibre(medico, prestacion);
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(agendaHorariosDiaDomainService.findAgendaHorarioActivoById(slot.getId())).thenReturn(slot);
        when(medicoDomainService.findMedicoActivoById(medico.getId())).thenReturn(medico);
        when(prestacionDomainService.findPrestacionActivaById(prestacion.getId())).thenReturn(prestacion);
        when(historicoEstadoPrestacionDomainService.getEstadoVigente(prestacion.getId())).thenReturn(EstadoPrestacion.NO_PUBLICADA);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.createTurno(TELEFONO, new CreateTurnoAgenteRequest(slot.getId())));

        assertEquals("PRESTACION_NO_PUBLICADA", excepcion.getCodigo());
        verify(agendaHorariosDiaDomainService, never()).occupyAgendaHorario(any());
    }

    @Test
    void createTurno_slotConPlazoDeReservaVencido_lanzaReglaNegocio() {
        AgendaHorariosDia slot = slotLibre(medico, prestacion);
        slot.setFechaLimiteReserva(ZonedDateTime.now().minusMinutes(1));
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(agendaHorariosDiaDomainService.findAgendaHorarioActivoById(slot.getId())).thenReturn(slot);
        when(medicoDomainService.findMedicoActivoById(medico.getId())).thenReturn(medico);
        when(prestacionDomainService.findPrestacionActivaById(prestacion.getId())).thenReturn(prestacion);
        when(historicoEstadoPrestacionDomainService.getEstadoVigente(prestacion.getId())).thenReturn(EstadoPrestacion.PUBLICADA);
        when(agendaHorariosDiaDomainService.findAgendaHorarioDisponible(slot.getId(), medico.getId(), prestacion.getId()))
                .thenReturn(slot);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.createTurno(TELEFONO, new CreateTurnoAgenteRequest(slot.getId())));

        assertEquals("AGENDA_HORARIO_FUERA_DE_PLAZO", excepcion.getCodigo());
        verify(agendaHorariosDiaDomainService, never()).occupyAgendaHorario(any());
    }

    //endregion

    //region ========== reprogramar ==========

    @Test
    void reprogramTurno_fueraDePlazo_lanzaReglaNegocio() {
        Turno turno = turnoVivo();
        turno.setFechaLimiteReprogramacion(ZonedDateTime.now().minusMinutes(1));
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo(turno.getCodigo())).thenReturn(turno);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.reprogramTurno(TELEFONO, turno.getCodigo(), new ReprogramTurnoAgenteRequest(UUID.randomUUID())));

        assertEquals("TURNO_FUERA_DE_PLAZO_REPROGRAMACION", excepcion.getCodigo());
        verify(historicoEstadoTurnoDomainService, never()).transitionToReprogramadoTurno(any());
    }

    @Test
    void reprogramTurno_casoFeliz_creaTurnoNuevoEnlazadoYReprogramaElViejo() {
        Turno turnoViejo = turnoVivo();
        AgendaHorariosDia slotNuevo = slotLibre(medico, prestacion);
        MedicoPrestacion medicoPrestacion = new MedicoPrestacion();
        medicoPrestacion.setPrecioParticular(BigDecimal.valueOf(8000));
        ReprogramTurnoAgenteResponse respuestaEsperada = mock(ReprogramTurnoAgenteResponse.class);

        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnoByCodigo(turnoViejo.getCodigo())).thenReturn(turnoViejo);
        when(agendaHorariosDiaDomainService.findAgendaHorarioDisponible(slotNuevo.getId(), medico.getId(), prestacion.getId()))
                .thenReturn(slotNuevo);
        when(medicoPrestacionDomainService.findVigenteEnFecha(medico.getId(), prestacion.getId(), slotNuevo.getFecha()))
                .thenReturn(medicoPrestacion);
        when(historicoEstadoPrestacionDomainService.getEstadoVigente(prestacion.getId())).thenReturn(EstadoPrestacion.PUBLICADA);
        when(indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(prestacion.getId()))
                .thenReturn(List.of());
        when(turnoDomainService.saveTurno(any(Turno.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(agenteMapper.toReprogramTurnoResponse(any(Turno.class), eq(EstadoTurno.PENDIENTE))).thenReturn(respuestaEsperada);

        ReprogramTurnoAgenteResponse respuesta = agenteApp.reprogramTurno(TELEFONO, turnoViejo.getCodigo(),
                new ReprogramTurnoAgenteRequest(slotNuevo.getId()));

        assertEquals(respuestaEsperada, respuesta);
        ArgumentCaptor<Turno> turnoCaptor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoDomainService).saveTurno(turnoCaptor.capture());
        assertEquals(turnoViejo, turnoCaptor.getValue().getTurnoOrigen());
        assertEquals(TipoCobertura.PARTICULAR, turnoCaptor.getValue().getTipoCobertura());
        verify(agendaHorariosDiaDomainService).occupyAgendaHorario(slotNuevo);
        verify(agendaHorariosDiaDomainService).releaseAgendaHorario(turnoViejo.getAgendaHorarios());
        verify(historicoEstadoTurnoDomainService).transitionToReprogramadoTurno(turnoViejo);
        ArgumentCaptor<TurnoNotificacionEvent> eventoCaptor = ArgumentCaptor.forClass(TurnoNotificacionEvent.class);
        verify(applicationEventPublisher).publishEvent(eventoCaptor.capture());
        assertEquals(TipoNotificacionTurno.REPROGRAMADO, eventoCaptor.getValue().tipo());
    }

    //endregion

    //region ========== listados ==========

    @Test
    void listTurnos_filtraPorElPacienteDelTelefonoYLosEstadosDeLaAccion() {
        Turno turno = turnoVivo();
        ListTurnoAgenteResponse itemEsperado = new ListTurnoAgenteResponse(
                turno.getCodigo(), turno.getFechaHoraInicio(), "Consulta clínica", "Ana", "Gómez", EstadoTurno.PENDIENTE);
        when(pacienteDomainService.findPacienteActivoByNumeroTelefono(TELEFONO)).thenReturn(paciente);
        when(turnoDomainService.findTurnosByPacienteIdAndEstadosVigentes(paciente.getId(), AccionTurnoAgente.CONFIRMABLE.estados()))
                .thenReturn(List.of(turno));
        when(historicoEstadoTurnoDomainService.getEstadosVigentes(anyList()))
                .thenReturn(Map.of(turno.getId(), EstadoTurno.PENDIENTE));
        when(agenteMapper.toListTurnoResponse(turno, EstadoTurno.PENDIENTE)).thenReturn(itemEsperado);

        List<ListTurnoAgenteResponse> respuesta = agenteApp.listTurnos(TELEFONO, AccionTurnoAgente.CONFIRMABLE);

        assertEquals(List.of(itemEsperado), respuesta);
        verify(turnoDomainService).findTurnosByPacienteIdAndEstadosVigentes(paciente.getId(), AccionTurnoAgente.CONFIRMABLE.estados());
    }

    @Test
    void listHorariosDisponibles_medicoQueNoAtiendeLaPrestacion_lanzaReglaNegocio() {
        when(prestacionDomainService.findPrestacionActivaByNombre("Consulta clínica")).thenReturn(prestacion);
        when(medicoDomainService.findMedicoActivoByMatricula("MP123")).thenReturn(medico);
        when(medicoPrestacionDomainService.existsVigenteEnFecha(eq(medico.getId()), eq(prestacion.getId()), any(LocalDate.class)))
                .thenReturn(false);

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> agenteApp.listHorariosDisponibles("Consulta clínica", "MP123", null, PageRequest.of(0, 10)));

        assertEquals("MEDICO_NO_ATIENDE_PRESTACION", excepcion.getCodigo());
        verify(agendaHorariosDiaQueryService, never()).findHorariosDisponibles(any(), any());
    }

    @Test
    void listHorariosDisponibles_conFranjaTarde_armaElCriteriaConMedicoPrestacionYRangoHorario() {
        when(prestacionDomainService.findPrestacionActivaByNombre("Consulta clínica")).thenReturn(prestacion);
        when(medicoDomainService.findMedicoActivoByMatricula("MP123")).thenReturn(medico);
        when(medicoPrestacionDomainService.existsVigenteEnFecha(eq(medico.getId()), eq(prestacion.getId()), any(LocalDate.class)))
                .thenReturn(true);
        when(agendaHorariosDiaQueryService.findHorariosDisponibles(any(), any()))
                .thenReturn(new com.accesmed.backend.Services.QueryServices.Filtering.PageResponse<>(List.of(), 0, 10, 0, 0));

        List<?> respuesta = agenteApp.listHorariosDisponibles("Consulta clínica", "MP123", FranjaHorariaAgente.TARDE, PageRequest.of(0, 10));

        assertTrue(respuesta.isEmpty());
        ArgumentCaptor<AgendaHorariosCriteria> criteriaCaptor = ArgumentCaptor.forClass(AgendaHorariosCriteria.class);
        verify(agendaHorariosDiaQueryService).findHorariosDisponibles(criteriaCaptor.capture(), any());
        AgendaHorariosCriteria criteria = criteriaCaptor.getValue();
        assertEquals(medico.getId(), criteria.getMedicoId().getEquals());
        assertEquals(prestacion.getId(), criteria.getPrestacionId().getEquals());
        assertEquals(LocalTime.NOON, criteria.getHoraDesde().getGreaterThanOrEqual());
        assertEquals(LocalTime.of(19, 0), criteria.getHoraDesde().getLessThan());
        assertNotNull(criteria.getPrestacionId());
        assertNull(criteria.getEstaOcupada());
    }

    //endregion

    //region ========== helpers ==========

    /**
     * Arma un turno vivo del paciente del test, con fechas límite en el futuro.
     *
     * @return {@code Turno} turno pendiente del paciente
     */
    private Turno turnoVivo() {
        Turno turno = new Turno();
        turno.setId(UUID.randomUUID());
        turno.setCodigo("TURNO_A1B2C3D4");
        turno.setPaciente(paciente);
        turno.setMedico(medico);
        turno.setPrestacion(prestacion);
        turno.setFechaHoraInicio(ZonedDateTime.now().plusDays(2));
        turno.setMontoAPagar(BigDecimal.valueOf(8000));
        turno.setFechaLimiteCancelacion(ZonedDateTime.now().plusDays(1));
        turno.setFechaLimiteConfirmacion(ZonedDateTime.now().plusDays(1));
        turno.setFechaLimiteReprogramacion(ZonedDateTime.now().plusDays(1));
        turno.setAgendaHorarios(slotLibre(medico, prestacion));
        return turno;
    }

    /**
     * Arma un slot libre y vigente del médico y la prestación indicados.
     *
     * @param medicoDelSlot {@code Medico} médico dueño de la agenda
     * @param prestacionDelSlot {@code Prestacion} prestación del slot
     * @return {@code AgendaHorariosDia} slot libre con plazo de reserva futuro
     */
    private AgendaHorariosDia slotLibre(Medico medicoDelSlot, Prestacion prestacionDelSlot) {
        AgendaMedico agendaMedico = new AgendaMedico();
        agendaMedico.setMedico(medicoDelSlot);
        AgendaHorariosDia slot = new AgendaHorariosDia();
        slot.setId(UUID.randomUUID());
        slot.setAgendaMedico(agendaMedico);
        slot.setPrestacion(prestacionDelSlot);
        slot.setFecha(LocalDate.now().plusDays(3));
        slot.setHoraDesde(LocalTime.of(10, 0));
        slot.setHoraHasta(LocalTime.of(10, 30));
        slot.setFechaLimiteReserva(ZonedDateTime.now().plusDays(2));
        slot.setEstaOcupada(false);
        return slot;
    }

    //endregion

}
