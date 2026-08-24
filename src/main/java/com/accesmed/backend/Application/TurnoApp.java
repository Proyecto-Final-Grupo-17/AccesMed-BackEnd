package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Domain.TipoCobertura;
import com.accesmed.backend.Services.Utils.ContextoCalculoMontoTurno;
import com.accesmed.backend.Services.Utils.EstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.FabricaEstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Records.Turno.Request.CancelTurnoRequest;
import com.accesmed.backend.Records.Turno.Request.CreateTurnoRequest;
import com.accesmed.backend.Records.Turno.Request.ReprogramTurnoRequest;
import com.accesmed.backend.Records.Turno.Request.ValidateTurnoRequest;
import com.accesmed.backend.Records.Turno.Response.CancelTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.CreateTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ReprogramTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ValidateTurnoResponse;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.TurnoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Turno. Orquesta el flujo completo de los cuatro métodos de
 * creación y manipulación de turnos (crear, reprogramar, cancelar, validar),
 * validando reglas de negocio y coordinando los services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoApp {

    //region ========== Dependencias o inyecciones ==========

    private final TurnoDomainService turnoDomainService;
    private final HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    private final AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final PacienteDomainService pacienteDomainService;
    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    private final IndicacionPrestacionTurnoDomainService indicacionPrestacionTurnoDomainService;
    private final FabricaEstrategiaCalcularMontoAPagarTurno fabricaEstrategiaCalcularMontoAPagarTurno;
    private final TurnoMapper turnoMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un turno nuevo para un paciente, médico y prestación, reservando un slot
     * de la agenda disponible y definiendo su estado inicial según si la prestación
     * requiere validación.
     *
     * @param createTurnoRequest {@code CreateTurnoRequest} datos del turno a crear
     * @return {@code CreateTurnoResponse} el turno creado con su estado inicial
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si alguno de
     *         los recursos (paciente, médico, prestación, slot) no existe o no está disponible
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación no está
     *         publicada, el slot está vencido o fuera de plazo, o el médico no atiende esa prestación
     */
    @Transactional
    public CreateTurnoResponse createTurno(CreateTurnoRequest createTurnoRequest) {

        log.info("Creación de turno iniciada: paciente={}, médico={}, prestación={}",
                createTurnoRequest.pacienteId(), createTurnoRequest.medicoId(), createTurnoRequest.prestacionId());

        //Buscar médico activo
        var medicoExistente = medicoDomainService.findMedicoActivoById(createTurnoRequest.medicoId());

        //Buscar prestación activa y validar que esté publicada
        var prestacionExistente = prestacionDomainService.findPrestacionActivaById(createTurnoRequest.prestacionId());
        var estadoVigentePrestacion = historicoEstadoPrestacionDomainService.getEstadoVigente(createTurnoRequest.prestacionId());
        if (estadoVigentePrestacion != EstadoPrestacion.PUBLICADA) {
            log.warn("No se pudo crear turno: prestación {} no está publicada, estado={}", createTurnoRequest.prestacionId(), estadoVigentePrestacion);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_PUBLICADA",
                    "La prestación no está publicada");
        }

        //Buscar y validar slot disponible
        var slotDisponible = agendaHorariosDiaDomainService.findAgendaHorarioDisponible(
                createTurnoRequest.slotId(), createTurnoRequest.medicoId(), createTurnoRequest.prestacionId());
        ZonedDateTime ahora = ZonedDateTime.now();
        if (ahora.isAfter(slotDisponible.getFechaLimiteReserva())) {
            log.warn("No se pudo crear turno: slot {} tiene plazo vencido", createTurnoRequest.slotId());
            throw new ReglaNegocioException(getClass(), "AGENDA_HORARIO_FUERA_DE_PLAZO",
                    "El plazo para reservar este horario ha vencido");
        }

        //Validar que el médico atienda esa prestación en esa fecha (y obtener la entidad para el precio)
        var medicoPrestacionExistente = medicoPrestacionDomainService.findVigenteEnFecha(
                createTurnoRequest.medicoId(), createTurnoRequest.prestacionId(),
                slotDisponible.getFechaLimiteReserva());

        //Buscar paciente
        var pacienteExistente = pacienteDomainService.findPacienteActivoById(createTurnoRequest.pacienteId());

        //Obtener indicaciones vigentes de la prestación (para copiar y decidir estado inicial)
        var indicacionesVigentes = indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(
                createTurnoRequest.prestacionId());
        boolean requiereValidacionAlguna = indicacionesVigentes.stream()
                .anyMatch(IndicacionPrestacion::getRequiereValidacion);

        //Marcar slot ocupado
        agendaHorariosDiaDomainService.occupyAgendaHorario(slotDisponible);

        //Construir y guardar el Turno
        var turnoNuevo = new Turno();
        turnoNuevo.setCodigo(generarCodigoTurno());
        turnoNuevo.setFechaHoraInicio(slotDisponible.getFechaLimiteReserva()
                .plus(prestacionExistente.getTiempoToleranciaSolicitud()));
        turnoNuevo.setPaciente(pacienteExistente);
        turnoNuevo.setMedico(medicoExistente);
        turnoNuevo.setPrestacion(prestacionExistente);
        turnoNuevo.setAgendaHorarios(slotDisponible);

        //Determinar tipo de cobertura y calcular monto a pagar
        BigDecimal montoAPagar;
        if (createTurnoRequest.obraSocialPacienteId() != null) {
            ObraSocialPaciente obraSocialPacienteExistente =
                    obraSocialPacienteDomainService.findObraSocialPacienteActivaById(createTurnoRequest.obraSocialPacienteId());

            ObraSocialPlanPrestacion coberturaExistente = obraSocialPlanPrestacionDomainService
                    .findCoberturaByPlanAndPrestacion(obraSocialPacienteExistente.getPlan().getId(), createTurnoRequest.prestacionId());

            EstrategiaCalcularMontoAPagarTurno estrategia =
                    fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(coberturaExistente.getModalidadCobertura());
            montoAPagar = estrategia.calcularMonto(new ContextoCalculoMontoTurno(
                    medicoPrestacionExistente.getPrecioParticular(),
                    coberturaExistente.getPorcentajeCobertura(),
                    coberturaExistente.getCoseguro()));

            turnoNuevo.setObraSocialPaciente(obraSocialPacienteExistente);
            turnoNuevo.setTipoCobertura(TipoCobertura.OBRA_SOCIAL);
        } else {
            montoAPagar = medicoPrestacionExistente.getPrecioParticular();
            turnoNuevo.setTipoCobertura(TipoCobertura.PARTICULAR);
        }
        turnoNuevo.setMontoAPagar(montoAPagar);

        //Calcular fechas límite usando tolerancias de la prestación
        turnoNuevo.setFechaLimiteValidacion(turnoNuevo.getFechaHoraInicio()
                .plus(prestacionExistente.getTiempoToleranciaValidacion()));
        turnoNuevo.setFechaLimiteReprogramacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaReprogramacion()));
        turnoNuevo.setFechaLimiteConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaConfirmacion()));
        turnoNuevo.setFechaLimiteCancelacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaCancelacion()));
        turnoNuevo.setFechaLimiteAnuncioTemprano(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaAnuncio().multipliedBy(2)));
        turnoNuevo.setFechaLimiteAnuncioTardio(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaAnuncio()));
        turnoNuevo.setFechaHoraRecordatorioConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoRecordatorioConfirmacion()));

        var turnoGuardado = turnoDomainService.saveTurno(turnoNuevo);

        //Guardar indicaciones del turno (copia de las vigentes, vinculadas a indicacionPrestacion)
        List<IndicacionPrestacionTurno> indicacionesTurnoNuevas = new ArrayList<>();
        for (IndicacionPrestacion indicacion : indicacionesVigentes) {
            var indicacionTurnoNueva = new IndicacionPrestacionTurno();
            indicacionTurnoNueva.setTurno(turnoGuardado);
            indicacionTurnoNueva.setIndicacionPrestacion(indicacion);
            indicacionesTurnoNuevas.add(indicacionTurnoNueva);
        }
        if (!indicacionesTurnoNuevas.isEmpty()) {
            indicacionPrestacionTurnoDomainService.saveAllIndicacionesPrestacionTurno(indicacionesTurnoNuevas);
        }

        //Abrir estado inicial del turno
        var estadoInicial = requiereValidacionAlguna ? EstadoTurno.ESPERA_VALIDACION : EstadoTurno.PENDIENTE;
        historicoEstadoTurnoDomainService.setEstadoInicialTurno(turnoGuardado, estadoInicial);

        //Mapear y devolver
        var createTurnoResponse = turnoMapper.toCreateResponse(turnoGuardado, estadoInicial);

        // TODO(observador): publicar TurnoNotificacionEvent(REGISTRADO, turnoGuardado)
        return createTurnoResponse;

    }

    /**
     * Reprograma un turno existente creando uno nuevo en un slot distinto,
     * enlazado por {@code turnoOrigen}, y cerrando el turno viejo en estado REPROGRAMADO.
     *
     * @param reprogramTurnoRequest {@code ReprogramTurnoRequest} id del turno original y nuevo slot
     * @return {@code ReprogramTurnoResponse} el turno nuevo reprogramado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el turno
     *         original o el nuevo slot no existen
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plazo de reprogramación
     *         venció, el nuevo slot no está disponible, o el turno está en un estado no permitido
     */
    @Transactional
    public ReprogramTurnoResponse reprogramTurno(ReprogramTurnoRequest reprogramTurnoRequest) {

        log.info("Reprogramación de turno iniciada: turnoId={}, slotId={}",
                reprogramTurnoRequest.id(), reprogramTurnoRequest.slotId());

        //Buscar el turno viejo
        var turnoViejo = turnoDomainService.findTurnoById(reprogramTurnoRequest.id());

        //Validar que no haya vencido el plazo de reprogramación
        ZonedDateTime ahora = ZonedDateTime.now();
        if (ahora.isAfter(turnoViejo.getFechaLimiteReprogramacion())) {
            log.warn("No se pudo reprogramar turno {}: plazo de reprogramación vencido", reprogramTurnoRequest.id());
            throw new ReglaNegocioException(getClass(), "TURNO_FUERA_DE_PLAZO_REPROGRAMACION",
                    "El plazo para reprogramar este turno ha vencido");
        }

        //Validar el nuevo slot contra los mismos criterios que createTurno
        var slotNuevo = agendaHorariosDiaDomainService.findAgendaHorarioDisponible(
                reprogramTurnoRequest.slotId(), turnoViejo.getMedico().getId(), turnoViejo.getPrestacion().getId());
        if (ahora.isAfter(slotNuevo.getFechaLimiteReserva())) {
            log.warn("No se pudo reprogramar turno {}: nuevo slot {} tiene plazo vencido",
                    reprogramTurnoRequest.id(), reprogramTurnoRequest.slotId());
            throw new ReglaNegocioException(getClass(), "AGENDA_HORARIO_FUERA_DE_PLAZO",
                    "El plazo para reservar el nuevo horario ha vencido");
        }

        //Revalidar que el médico siga atendiendo esa prestación en la nueva fecha (y obtener la entidad para el precio)
        var medicoPrestacionVigenteEnFecha = medicoPrestacionDomainService.findVigenteEnFecha(
                turnoViejo.getMedico().getId(), turnoViejo.getPrestacion().getId(),
                slotNuevo.getFechaLimiteReserva());

        //Revalidar que la prestación siga publicada
        var estadoVigentePrestacion = historicoEstadoPrestacionDomainService.getEstadoVigente(turnoViejo.getPrestacion().getId());
        if (estadoVigentePrestacion != EstadoPrestacion.PUBLICADA) {
            log.warn("No se pudo reprogramar turno {}: prestación {} no está publicada",
                    reprogramTurnoRequest.id(), turnoViejo.getPrestacion().getId());
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_PUBLICADA",
                    "La prestación no está publicada");
        }

        //Recalcular indicaciones (por si cambiaron)
        var indicacionesVigentes = indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(
                turnoViejo.getPrestacion().getId());
        boolean requiereValidacionAlguna = indicacionesVigentes.stream()
                .anyMatch(IndicacionPrestacion::getRequiereValidacion);

        //Ocupar el nuevo slot y liberar el viejo
        agendaHorariosDiaDomainService.occupyAgendaHorario(slotNuevo);
        agendaHorariosDiaDomainService.releaseAgendaHorario(turnoViejo.getAgendaHorarios());

        //Crear el turno nuevo enlazado por turnoOrigen
        var turnoNuevo = new Turno();
        turnoNuevo.setCodigo(generarCodigoTurno());
        turnoNuevo.setFechaHoraInicio(slotNuevo.getFechaLimiteReserva()
                .plus(turnoViejo.getPrestacion().getTiempoToleranciaSolicitud()));
        turnoNuevo.setPaciente(turnoViejo.getPaciente());
        turnoNuevo.setMedico(turnoViejo.getMedico());
        turnoNuevo.setPrestacion(turnoViejo.getPrestacion());
        turnoNuevo.setAgendaHorarios(slotNuevo);
        turnoNuevo.setTurnoOrigen(turnoViejo);

        //Recalcular tipo de cobertura y monto a pagar (por si cambiaron entre reprogramaciones)
        BigDecimal montoAPagarReprogramado;
        if (turnoViejo.getObraSocialPaciente() != null) {
            ObraSocialPlanPrestacion coberturaActual = obraSocialPlanPrestacionDomainService
                    .findCoberturaByPlanAndPrestacion(turnoViejo.getObraSocialPaciente().getPlan().getId(),
                            turnoViejo.getPrestacion().getId());

            EstrategiaCalcularMontoAPagarTurno estrategia =
                    fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(coberturaActual.getModalidadCobertura());
            montoAPagarReprogramado = estrategia.calcularMonto(new ContextoCalculoMontoTurno(
                    medicoPrestacionVigenteEnFecha.getPrecioParticular(),
                    coberturaActual.getPorcentajeCobertura(),
                    coberturaActual.getCoseguro()));

            turnoNuevo.setObraSocialPaciente(turnoViejo.getObraSocialPaciente());
            turnoNuevo.setTipoCobertura(TipoCobertura.OBRA_SOCIAL);
        } else {
            montoAPagarReprogramado = medicoPrestacionVigenteEnFecha.getPrecioParticular();
            turnoNuevo.setTipoCobertura(TipoCobertura.PARTICULAR);
        }
        turnoNuevo.setMontoAPagar(montoAPagarReprogramado);

        //Copiar fechas límite del viejo (mismo médico, prestación, tipo de cobertura)
        turnoNuevo.setFechaLimiteValidacion(turnoNuevo.getFechaHoraInicio()
                .plus(turnoViejo.getPrestacion().getTiempoToleranciaValidacion()));
        turnoNuevo.setFechaLimiteReprogramacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaReprogramacion()));
        turnoNuevo.setFechaLimiteConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaConfirmacion()));
        turnoNuevo.setFechaLimiteCancelacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaCancelacion()));
        turnoNuevo.setFechaLimiteAnuncioTemprano(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaAnuncio().multipliedBy(2)));
        turnoNuevo.setFechaLimiteAnuncioTardio(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaAnuncio()));
        turnoNuevo.setFechaHoraRecordatorioConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoRecordatorioConfirmacion()));

        var turnoNuevoGuardado = turnoDomainService.saveTurno(turnoNuevo);

        //Transicionar el turno viejo a REPROGRAMADO
        historicoEstadoTurnoDomainService.transitionToReprogramadoTurno(turnoViejo);

        //Abrir estado inicial del turno nuevo
        var estadoInicial = requiereValidacionAlguna ? EstadoTurno.ESPERA_VALIDACION : EstadoTurno.PENDIENTE;
        historicoEstadoTurnoDomainService.setEstadoInicialTurno(turnoNuevoGuardado, estadoInicial);

        //Mapear y devolver
        var reprogramTurnoResponse = turnoMapper.toReprogramResponse(turnoNuevoGuardado, estadoInicial);

        // TODO(observador): publicar TurnoNotificacionEvent(REPROGRAMADO, turnoNuevoGuardado)
        return reprogramTurnoResponse;

    }

    /**
     * Cancela un turno existente, liberando su slot y transicionándolo a estado CANCELADO.
     *
     * @param cancelTurnoRequest {@code CancelTurnoRequest} id del turno y motivo (opcional)
     * @return {@code CancelTurnoResponse} el turno cancelado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el turno no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el turno está en un
     *         estado final y no puede ser cancelado
     */
    @Transactional
    public CancelTurnoResponse cancelTurno(CancelTurnoRequest cancelTurnoRequest) {

        log.info("Cancelación de turno iniciada: turnoId={}", cancelTurnoRequest.id());

        //Buscar el turno
        var turnoExistente = turnoDomainService.findTurnoById(cancelTurnoRequest.id());

        //Liberar el slot
        agendaHorariosDiaDomainService.releaseAgendaHorario(turnoExistente.getAgendaHorarios());

        //Establecer motivo de cancelación (por defecto SOLICITUD_DEL_PACIENTE)
        var motivoCancelacion = cancelTurnoRequest.motivo() != null
                ? cancelTurnoRequest.motivo() : MotivoCancelacion.SOLICITUD_DEL_PACIENTE;
        turnoExistente.setMotivoCancelacion(motivoCancelacion);
        turnoDomainService.saveTurno(turnoExistente);

        //Transicionar a CANCELADO (valida que no esté en estado final)
        historicoEstadoTurnoDomainService.transitionToCanceladoTurno(turnoExistente);

        //Mapear y devolver
        var cancelTurnoResponse = turnoMapper.toCancelResponse(turnoExistente, EstadoTurno.CANCELADO);

        // TODO(observador): publicar TurnoNotificacionEvent(motivoCancelacion == VALIDACION_VENCIDA || VALIDACION_RECHAZADA ? NO_VALIDADO : CANCELADO, turno)
        return cancelTurnoResponse;

    }

    /**
     * Valida (aprueba o rechaza) un turno en estado ESPERA_VALIDACION.
     *
     * @param validateTurnoRequest {@code ValidateTurnoRequest} id del turno y aprobado (boolean)
     * @return {@code ValidateTurnoResponse} el turno con su nuevo estado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el turno no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el turno no está
     *         en estado ESPERA_VALIDACION
     */
    @Transactional
    public ValidateTurnoResponse validateTurno(ValidateTurnoRequest validateTurnoRequest) {

        log.info("Validación de turno iniciada: turnoId={}, aprobado={}",
                validateTurnoRequest.id(), validateTurnoRequest.aprobado());

        //Buscar el turno
        var turnoExistente = turnoDomainService.findTurnoById(validateTurnoRequest.id());

        EstadoTurno estadoResultante;

        //Obtener indicaciones del turno para marcar validadas si corresponde
        List<IndicacionPrestacionTurno> indicacionesTurno = indicacionPrestacionTurnoDomainService
                .findByTurnoId(turnoExistente.getId());

        if (validateTurnoRequest.aprobado()) {
            //Transición ESPERA_VALIDACION → PENDIENTE
            historicoEstadoTurnoDomainService.transitionEsperaValidacionToPendienteTurno(turnoExistente);
            estadoResultante = EstadoTurno.PENDIENTE;
            //Marcar indicaciones como validadas
            indicacionPrestacionTurnoDomainService.marcarValidadas(indicacionesTurno);
        } else {
            //Rechazar: liberar slot, establecer motivo y transicionar
            agendaHorariosDiaDomainService.releaseAgendaHorario(turnoExistente.getAgendaHorarios());
            turnoExistente.setMotivoCancelacion(MotivoCancelacion.VALIDACION_RECHAZADA);
            turnoDomainService.saveTurno(turnoExistente);
            historicoEstadoTurnoDomainService.transitionEsperaValidacionToCanceladoTurno(turnoExistente);
            estadoResultante = EstadoTurno.CANCELADO;
            //Marcar indicaciones como validadas (aunque sea rechazado, se marca la validación)
            indicacionPrestacionTurnoDomainService.marcarValidadas(indicacionesTurno);
        }

        //Mapear y devolver
        var validateTurnoResponse = turnoMapper.toValidateResponse(turnoExistente, estadoResultante);

        // TODO(observador): publicar TurnoNotificacionEvent(...)
        return validateTurnoResponse;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Genera un código único para un turno. Formato simple: "TURNO_" + UUID.
     *
     * @return {@code String} código generado
     */
    private String generarCodigoTurno() {
        return "TURNO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    //endregion

}
