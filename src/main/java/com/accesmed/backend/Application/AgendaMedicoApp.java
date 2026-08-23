package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.AgendaMedico.Request.BloqueHorarioRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.CreateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.DiaPatronRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.DiaSueltoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.HorarioAAgregarRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.UpdateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.UpdateVigenciaAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Response.CreateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.DiaAgendaResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.UpdateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.UpdateVigenciaAgendaMedicoResponse;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.AgendaMedicoDomainService;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.AgendaMedicoMapper;
import com.accesmed.backend.Services.Utils.GeneradorSlotsAgenda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso de Agenda. Orquesta el flujo completo del agregado {@code AgendaMedico} /
 * {@code AgendaHorariosDia}: alta con expansión de patrón, delta de composición sobre los
 * slots, movimiento del período de vigencia y los tres listados con filtrado dinámico.
 * Coordina médico, prestación, su estado vigente, la vigencia de {@code MedicoPrestacion}
 * y los parámetros de la clínica — toda la orquestación multi-entidad vive acá, nunca en
 * un {@code DomainService} (ver {@code ARQUITECTURA.md §5.0}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaMedicoApp {

    //region ========== Constantes ==========

    /**
     * Tope operativo de slots que un único {@code Confirmar} puede generar. Guardarraíl
     * operativo, no regla de negocio (§5 AGEN).
     */
    private static final int MAX_SLOTS_POR_CONFIRMAR = 500;

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final AgendaMedicoDomainService agendaMedicoDomainService;
    private final AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final ClinicaDomainService clinicaDomainService;

    private final AgendaMedicoMapper agendaMedicoMapper;
    private final GeneradorSlotsAgenda generadorSlotsAgenda;

    //endregion

    //region ========== Métodos de escritura ==========

    /**
     * Crea un período de agenda nuevo, expandiendo el patrón semanal o los días sueltos a
     * {@code AgendaHorariosDia}. El patrón no se persiste: se descarta después de expandirlo.
     *
     * @param createAgendaMedicoRequest {@code CreateAgendaMedicoRequest} datos del período y del patrón
     * @return {@code CreateAgendaMedicoResponse} la agenda creada, con sus días y horarios expandidos
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el médico no existe activo
     * @throws ValidacionException {@code ValidacionException} con los errores de forma del lote
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el período se solapa con otro del
     *         médico, o si el lote supera el tope de generación
     */
    @Transactional
    public CreateAgendaMedicoResponse createAgendaMedico(CreateAgendaMedicoRequest createAgendaMedicoRequest) {

        log.info("Creación de agenda médica iniciada: médico={}", createAgendaMedicoRequest.medicoId());

        Medico medicoExistente = medicoDomainService.findMedicoActivoById(createAgendaMedicoRequest.medicoId());

        validatePeriodoAlta(createAgendaMedicoRequest.fechaHoraInicioVigencia(), createAgendaMedicoRequest.fechaHoraFinVigencia());
        agendaMedicoDomainService.validateSinSolapamiento(medicoExistente.getId(),
                createAgendaMedicoRequest.fechaHoraInicioVigencia(), createAgendaMedicoRequest.fechaHoraFinVigencia(), null);

        Clinica clinica = clinicaDomainService.findClinica();
        ZoneId zonaHorariaClinica = ZoneId.of(clinica.getZonaHoraria());

        List<BloqueConFecha> bloquesConFecha = expandirBloques(createAgendaMedicoRequest, zonaHorariaClinica);

        Set<UUID> prestacionIds = bloquesConFecha.stream()
                .map(bloqueConFecha -> bloqueConFecha.bloque().prestacionId())
                .collect(Collectors.toSet());
        Map<UUID, Prestacion> prestacionesPorId = resolvePrestacionesPublicadas(prestacionIds);

        validateMedicoPrestacionVigenteEnFechas(medicoExistente.getId(), bloquesConFecha.stream()
                .map(bloqueConFecha -> new FechaPrestacion(bloqueConFecha.fecha(), bloqueConFecha.bloque().prestacionId()))
                .toList(), zonaHorariaClinica);

        List<GeneradorSlotsAgenda.BloqueAGenerar> bloquesAGenerar = bloquesConFecha.stream()
                .map(bloqueConFecha -> new GeneradorSlotsAgenda.BloqueAGenerar(bloqueConFecha.fecha(),
                        bloqueConFecha.bloque().horaDesde(), bloqueConFecha.bloque().horaHasta(),
                        prestacionesPorId.get(bloqueConFecha.bloque().prestacionId()), bloqueConFecha.bloque().duracionTurno()))
                .toList();

        List<GeneradorSlotsAgenda.SlotGenerado> slotsGenerados = generadorSlotsAgenda.generar(bloquesAGenerar,
                clinica.getHorarioInicioAtencion(), clinica.getHorarioFinAtencion(), Map.of(), zonaHorariaClinica);

        validateTopeGeneracion(slotsGenerados.size());

        AgendaMedico agendaMedicoNueva = agendaMedicoMapper.toEntity(createAgendaMedicoRequest);
        agendaMedicoNueva.setMedico(medicoExistente);
        AgendaMedico agendaMedicoGuardada = agendaMedicoDomainService.saveAgendaMedico(agendaMedicoNueva);

        List<AgendaHorariosDia> horariosAGuardar = slotsGenerados.stream()
                .map(slot -> construirAgendaHorariosDia(agendaMedicoGuardada, slot))
                .toList();

        List<AgendaHorariosDia> horariosGuardados = agendaHorariosDiaDomainService.saveAllAgendaHorariosDia(horariosAGuardar);

        List<DiaAgendaResponse> diasResponse = agendaMedicoMapper.toDiaAgendaResponses(horariosGuardados);

        CreateAgendaMedicoResponse createAgendaMedicoResponse = agendaMedicoMapper.toCreateResponse(
                agendaMedicoGuardada, diasResponse, horariosGuardados.size());
        return createAgendaMedicoResponse;

    }

    /**
     * Aplica un delta de composición sobre los slots de una agenda: altas y bajas de
     * {@code AgendaHorariosDia}. Orden de la transacción: resolver la unión de bajas
     * (directas por id más las arrastradas por {@code fechasAExcluir}), validar la guarda
     * restrictiva contra slots ocupados, aplicar las bajas y, por último, aplicar las altas.
     *
     * @param updateAgendaMedicoRequest {@code UpdateAgendaMedicoRequest} delta a aplicar,
     *        incluyendo el id de la agenda (ya validado contra la ruta en el Controller)
     * @return {@code UpdateAgendaMedicoResponse} los conteos de cada efecto aplicado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la agenda no existe
     * @throws ValidacionException {@code ValidacionException} si el delta es contradictorio o el
     *         lote de altas tiene errores de forma
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la baja arrastra algún slot ocupado
     */
    @Transactional
    public UpdateAgendaMedicoResponse updateAgendaMedico(UpdateAgendaMedicoRequest updateAgendaMedicoRequest) {

        UUID id = updateAgendaMedicoRequest.id();

        log.info("Actualización de agenda médica iniciada: id={}", id);

        //Paso 1
        AgendaMedico agendaMedicoExistente = agendaMedicoDomainService.findAgendaMedicoById(id);

        List<UUID> horariosAExcluirIds = nullToEmpty(updateAgendaMedicoRequest.horariosAExcluir());
        List<LocalDate> fechasAExcluir = nullToEmpty(updateAgendaMedicoRequest.fechasAExcluir());
        List<HorarioAAgregarRequest> horariosAAgregar = nullToEmpty(updateAgendaMedicoRequest.horariosAAgregar());

        validateSinContradiccion(fechasAExcluir, horariosAAgregar);

        //Paso 2: resolver la unión de horarios a dar de baja (directos + arrastrados por fecha)
        List<AgendaHorariosDia> horariosDirectosAExcluir = agendaHorariosDiaDomainService.findAgendaHorariosByIds(horariosAExcluirIds);
        List<AgendaHorariosDia> horariosPorFechasAExcluir = agendaHorariosDiaDomainService
                .findByAgendaMedicoIdAndFechas(id, fechasAExcluir);

        Map<UUID, AgendaHorariosDia> horariosAEliminarPorId = new LinkedHashMap<>();
        horariosDirectosAExcluir.forEach(horario -> horariosAEliminarPorId.put(horario.getId(), horario));
        horariosPorFechasAExcluir.forEach(horario -> horariosAEliminarPorId.put(horario.getId(), horario));
        List<AgendaHorariosDia> horariosAEliminarTotal = new ArrayList<>(horariosAEliminarPorId.values());

        //Paso 3: guarda restrictiva sobre la unión, antes de escribir nada
        agendaHorariosDiaDomainService.validateSinOcupados(horariosAEliminarPorId.keySet());

        //Paso 4: aplicar bajas y altas
        agendaHorariosDiaDomainService.softDeleteAll(horariosAEliminarTotal, "Excluido por actualización de agenda");

        List<AgendaHorariosDia> horariosGuardados = aplicarHorariosAAgregar(agendaMedicoExistente, horariosAAgregar);

        UpdateAgendaMedicoResponse updateAgendaMedicoResponse = new UpdateAgendaMedicoResponse(id,
                horariosGuardados.size(), horariosAEliminarTotal.size(), fechasAExcluir.size());
        return updateAgendaMedicoResponse;

    }

    /**
     * Actualiza el período de vigencia de una agenda: mover el inicio (solo si no arrancó),
     * adelantar el fin (restrictivo, con baja en cascada de los horarios posteriores) o
     * atrasarlo. El no solapamiento con otros períodos del médico lo valida igual que el
     * alta.
     *
     * @param updateVigenciaAgendaMedicoRequest {@code UpdateVigenciaAgendaMedicoRequest} nuevas
     *        fechas, incluyendo el id de la agenda (ya validado contra la ruta en el Controller)
     * @return {@code UpdateVigenciaAgendaMedicoResponse} la vigencia actualizada y la cantidad de
     *         horarios dados de baja al adelantar el fin
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la agenda no existe
     * @throws ValidacionException {@code ValidacionException} si se intenta mover un inicio ya
     *         arrancado, o el período resultante es inválido
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el nuevo período se solapa con
     *         otro del médico, o si adelantar el fin arrastra algún slot ocupado
     */
    @Transactional
    public UpdateVigenciaAgendaMedicoResponse updateVigenciaAgendaMedico(UpdateVigenciaAgendaMedicoRequest updateVigenciaAgendaMedicoRequest) {

        UUID id = updateVigenciaAgendaMedicoRequest.id();

        log.info("Actualización de vigencia de agenda médica iniciada: id={}", id);

        AgendaMedico agendaMedicoExistente = agendaMedicoDomainService.findAgendaMedicoById(id);

        ZonedDateTime ahora = ZonedDateTime.now();
        ZonedDateTime nuevoInicio = updateVigenciaAgendaMedicoRequest.fechaHoraInicioVigencia() != null
                ? updateVigenciaAgendaMedicoRequest.fechaHoraInicioVigencia() : agendaMedicoExistente.getFechaHoraInicioVigencia();
        ZonedDateTime nuevoFin = updateVigenciaAgendaMedicoRequest.fechaHoraFinVigencia() != null
                ? updateVigenciaAgendaMedicoRequest.fechaHoraFinVigencia() : agendaMedicoExistente.getFechaHoraFinVigencia();

        List<String> errores = new ArrayList<>();
        if (updateVigenciaAgendaMedicoRequest.fechaHoraInicioVigencia() != null
                && !agendaMedicoExistente.getFechaHoraInicioVigencia().isAfter(ahora)) {
            errores.add("No se puede mover el inicio de vigencia de una agenda que ya arrancó.");
        }
        if (!nuevoInicio.isBefore(nuevoFin)) {
            errores.add("La fecha de inicio de vigencia debe ser anterior a la fecha de fin.");
        }
        if (!errores.isEmpty()) {
            log.warn("No se pudo actualizar la vigencia de la agenda {}: {}", id, errores);
            throw new ValidacionException(getClass(), errores);
        }

        agendaMedicoDomainService.validateSinSolapamiento(agendaMedicoExistente.getMedico().getId(), nuevoInicio, nuevoFin, id);

        int cantidadHorariosDadosDeBaja = 0;
        boolean seAdelantaFin = updateVigenciaAgendaMedicoRequest.fechaHoraFinVigencia() != null
                && nuevoFin.isBefore(agendaMedicoExistente.getFechaHoraFinVigencia());

        if (seAdelantaFin) {

            List<AgendaHorariosDia> horariosPosteriores = agendaHorariosDiaDomainService.findHorariosPosteriores(id, nuevoFin.toLocalDate());
            Set<UUID> idsHorariosPosteriores = horariosPosteriores.stream().map(AgendaHorariosDia::getId).collect(Collectors.toSet());

            agendaHorariosDiaDomainService.validateSinOcupados(idsHorariosPosteriores);

            agendaHorariosDiaDomainService.softDeleteAll(horariosPosteriores, "Excluido por adelanto de fin de vigencia de agenda");
            cantidadHorariosDadosDeBaja = horariosPosteriores.size();

        }

        agendaMedicoExistente.setFechaHoraInicioVigencia(nuevoInicio);
        agendaMedicoExistente.setFechaHoraFinVigencia(nuevoFin);
        AgendaMedico agendaMedicoActualizada = agendaMedicoDomainService.saveAgendaMedico(agendaMedicoExistente);

        UpdateVigenciaAgendaMedicoResponse updateVigenciaAgendaMedicoResponse = new UpdateVigenciaAgendaMedicoResponse(
                agendaMedicoActualizada.getId(), agendaMedicoActualizada.getFechaHoraInicioVigencia(),
                agendaMedicoActualizada.getFechaHoraFinVigencia(), cantidadHorariosDadosDeBaja);
        return updateVigenciaAgendaMedicoResponse;

    }

    //endregion

    //region ========== Helpers privados ==========

    /**
     * Bloque horario ya resuelto a una fecha concreta, intermedio entre el request
     * (patrón o días sueltos) y {@code GeneradorSlotsAgenda}.
     */
    private record BloqueConFecha(LocalDate fecha, BloqueHorarioRequest bloque) {

    }

    /**
     * Par (fecha, prestación) a validar contra la vigencia de {@code MedicoPrestacion}.
     */
    private record FechaPrestacion(LocalDate fecha, UUID prestacionId) {

    }

    private List<BloqueConFecha> expandirBloques(CreateAgendaMedicoRequest createAgendaMedicoRequest, ZoneId zonaHorariaClinica) {

        //El inicio/fin de vigencia vienen como instante con el offset del cliente: hay que
        //resolverlos a fecha de calendario con la zona horaria de la clínica, no con la del
        //cliente, para que el patrón semanal se expanda sobre los días que la clínica
        //considera reales.
        LocalDate fechaInicio = createAgendaMedicoRequest.fechaHoraInicioVigencia().withZoneSameInstant(zonaHorariaClinica).toLocalDate();
        LocalDate fechaFin = createAgendaMedicoRequest.fechaHoraFinVigencia().withZoneSameInstant(zonaHorariaClinica).toLocalDate();
        List<BloqueConFecha> bloquesConFecha = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        if (createAgendaMedicoRequest.patronSemanal() != null && !createAgendaMedicoRequest.patronSemanal().isEmpty()) {

            for (DiaPatronRequest diaPatron : createAgendaMedicoRequest.patronSemanal()) {
                for (LocalDate fecha = fechaInicio; !fecha.isAfter(fechaFin); fecha = fecha.plusDays(1)) {
                    if (fecha.getDayOfWeek() == diaPatron.diaSemana()) {
                        for (BloqueHorarioRequest bloque : diaPatron.bloques()) {
                            bloquesConFecha.add(new BloqueConFecha(fecha, bloque));
                        }
                    }
                }
            }

        } else {

            for (DiaSueltoRequest diaSuelto : createAgendaMedicoRequest.diasSueltos()) {
                if (diaSuelto.fecha().isBefore(fechaInicio) || diaSuelto.fecha().isAfter(fechaFin)) {
                    errores.add("La fecha " + diaSuelto.fecha() + " está fuera del período de vigencia de la agenda.");
                    continue;
                }
                for (BloqueHorarioRequest bloque : diaSuelto.bloques()) {
                    bloquesConFecha.add(new BloqueConFecha(diaSuelto.fecha(), bloque));
                }
            }

        }

        if (!errores.isEmpty()) {
            log.warn("No se pudo crear la agenda médica: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

        return bloquesConFecha;

    }

    private void validatePeriodoAlta(ZonedDateTime fechaHoraInicioVigencia, ZonedDateTime fechaHoraFinVigencia) {

        List<String> errores = new ArrayList<>();
        if (fechaHoraInicioVigencia.isBefore(ZonedDateTime.now())) {
            errores.add("La fecha de inicio de vigencia no puede ser anterior a ahora.");
        }
        if (!fechaHoraInicioVigencia.isBefore(fechaHoraFinVigencia)) {
            errores.add("La fecha de inicio de vigencia debe ser anterior a la fecha de fin.");
        }
        if (!errores.isEmpty()) {
            log.warn("No se pudo crear la agenda médica: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    /**
     * Resuelve un lote de prestaciones a sus entidades activas y publicadas, en 2 consultas
     * en total (Fase 3 bis #3): una para las prestaciones activas del lote (batch, en vez de
     * un {@code find} + {@code try/catch} por prestación) y otra para sus estados vigentes
     * ({@link HistoricoEstadoPrestacionDomainService#getEstadosVigentes}, que ya existe).
     * Los ids pedidos que no vuelven entre las activas son los inexistentes o deshabilitados.
     *
     * @param prestacionIds {@code Set<UUID>} identificadores de las prestaciones del lote
     * @return {@code Map<UUID, Prestacion>} las prestaciones activas y publicadas, por id
     * @throws ValidacionException {@code ValidacionException} si alguna prestación no existe,
     *         está deshabilitada o no está publicada
     */
    private Map<UUID, Prestacion> resolvePrestacionesPublicadas(Set<UUID> prestacionIds) {

        List<Prestacion> prestacionesActivas = prestacionDomainService.findPrestacionesActivasByIds(prestacionIds);
        Map<UUID, Prestacion> prestacionesActivasPorId = prestacionesActivas.stream()
                .collect(Collectors.toMap(Prestacion::getId, prestacion -> prestacion));

        Map<UUID, EstadoPrestacion> estadosVigentes = historicoEstadoPrestacionDomainService.getEstadosVigentes(prestacionIds);

        List<String> errores = new ArrayList<>();
        for (UUID prestacionId : prestacionIds) {
            if (!prestacionesActivasPorId.containsKey(prestacionId)) {
                errores.add("La prestación " + prestacionId + " no existe o está deshabilitada.");
            } else if (estadosVigentes.get(prestacionId) != EstadoPrestacion.PUBLICADA) {
                errores.add("La prestación " + prestacionId + " no está publicada.");
            }
        }

        if (!errores.isEmpty()) {
            log.warn("No se pudo resolver el lote de prestaciones de la agenda: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

        return prestacionesActivasPorId;

    }

    /**
     * Valida que el médico tenga la prestación vigente en cada fecha del lote, en una sola
     * consulta (Fase 3 bis #2): trae los períodos de {@code MedicoPrestacion} del médico
     * para las prestaciones del lote una única vez y evalúa cada fecha en memoria, con la
     * misma semántica que la consulta puntual ({@code inicio <= fecha AND (fin IS NULL OR
     * fecha < fin)}). Los pares se deduplican antes de validar.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param fechasPrestacion {@code List<FechaPrestacion>} pares (fecha, prestación) a validar
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, con la que se
     *        resuelve cada fecha a instante absoluto
     * @throws ValidacionException {@code ValidacionException} si el médico no tiene alguna
     *         de las prestaciones vigente en la fecha correspondiente
     */
    private void validateMedicoPrestacionVigenteEnFechas(UUID medicoId, List<FechaPrestacion> fechasPrestacion,
            ZoneId zonaHorariaClinica) {

        List<FechaPrestacion> paresUnicos = fechasPrestacion.stream().distinct().toList();

        Set<UUID> prestacionIds = paresUnicos.stream().map(FechaPrestacion::prestacionId).collect(Collectors.toSet());
        Map<UUID, List<MedicoPrestacion>> asignacionesPorPrestacion = medicoPrestacionDomainService
                .findAsignacionesByMedicoAndPrestaciones(medicoId, prestacionIds).stream()
                .collect(Collectors.groupingBy(medicoPrestacion -> medicoPrestacion.getPrestacion().getId()));

        List<String> errores = paresUnicos.stream()
                .filter(fechaPrestacion -> {
                    ZonedDateTime instanteFecha = fechaPrestacion.fecha().atStartOfDay(zonaHorariaClinica);
                    List<MedicoPrestacion> asignaciones = asignacionesPorPrestacion.getOrDefault(fechaPrestacion.prestacionId(), List.of());
                    return asignaciones.stream().noneMatch(asignacion -> !asignacion.getFechaInicioVigencia().isAfter(instanteFecha)
                            && (asignacion.getFechaFinVigencia() == null || instanteFecha.isBefore(asignacion.getFechaFinVigencia())));
                })
                .map(fechaPrestacion -> "El médico no tiene la prestación " + fechaPrestacion.prestacionId()
                        + " vigente en la fecha " + fechaPrestacion.fecha() + ".")
                .toList();

        if (!errores.isEmpty()) {
            log.warn("No se pudo crear la agenda médica: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    private void validateTopeGeneracion(int cantidadSlots) {

        if (cantidadSlots > MAX_SLOTS_POR_CONFIRMAR) {
            log.warn("No se pudo generar la agenda: {} slots supera el tope operativo de {}", cantidadSlots, MAX_SLOTS_POR_CONFIRMAR);
            throw new ReglaNegocioException(getClass(), "AGENDA_LIMITE_GENERACION_EXCEDIDO",
                    "El lote genera " + cantidadSlots + " horarios, superando el tope operativo de " + MAX_SLOTS_POR_CONFIRMAR
                            + " por confirmación. Reducí el período o el patrón e intentá de nuevo.");
        }

    }

    private AgendaHorariosDia construirAgendaHorariosDia(AgendaMedico agendaMedico, GeneradorSlotsAgenda.SlotGenerado slot) {

        AgendaHorariosDia agendaHorariosDiaNueva = new AgendaHorariosDia();
        agendaHorariosDiaNueva.setAgendaMedico(agendaMedico);
        agendaHorariosDiaNueva.setFecha(slot.fecha());
        agendaHorariosDiaNueva.setPrestacion(slot.prestacion());
        agendaHorariosDiaNueva.setHoraDesde(slot.horaDesde());
        agendaHorariosDiaNueva.setHoraHasta(slot.horaHasta());
        agendaHorariosDiaNueva.setFechaLimiteReserva(slot.fechaLimiteReserva());
        agendaHorariosDiaNueva.setEstaOcupada(false);
        return agendaHorariosDiaNueva;

    }

    private void validateSinContradiccion(List<LocalDate> fechasAExcluir, List<HorarioAAgregarRequest> horariosAAgregar) {

        Set<LocalDate> fechasAExcluirSet = Set.copyOf(fechasAExcluir);

        List<String> errores = horariosAAgregar.stream()
                .filter(horario -> fechasAExcluirSet.contains(horario.fecha()))
                .map(horario -> "No se puede agregar un horario el " + horario.fecha()
                        + " y excluir esa misma fecha en el mismo request.")
                .distinct()
                .toList();

        if (!errores.isEmpty()) {
            log.warn("No se pudo actualizar la agenda médica: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    private List<AgendaHorariosDia> aplicarHorariosAAgregar(AgendaMedico agendaMedico, List<HorarioAAgregarRequest> horariosAAgregar) {

        if (horariosAAgregar.isEmpty()) {
            return List.of();
        }

        List<String> erroresFecha = horariosAAgregar.stream()
                .filter(horario -> horario.fecha().isBefore(agendaMedico.getFechaHoraInicioVigencia().toLocalDate())
                        || horario.fecha().isAfter(agendaMedico.getFechaHoraFinVigencia().toLocalDate()))
                .map(horario -> "La fecha " + horario.fecha() + " está fuera del período de vigencia de la agenda.")
                .distinct()
                .toList();
        if (!erroresFecha.isEmpty()) {
            log.warn("No se pudo actualizar la agenda médica: {}", erroresFecha);
            throw new ValidacionException(getClass(), erroresFecha);
        }

        Set<UUID> prestacionIds = horariosAAgregar.stream().map(HorarioAAgregarRequest::prestacionId).collect(Collectors.toSet());
        Map<UUID, Prestacion> prestacionesPorId = resolvePrestacionesPublicadas(prestacionIds);

        Clinica clinica = clinicaDomainService.findClinica();
        ZoneId zonaHorariaClinica = ZoneId.of(clinica.getZonaHoraria());

        validateMedicoPrestacionVigenteEnFechas(agendaMedico.getMedico().getId(), horariosAAgregar.stream()
                .map(horario -> new FechaPrestacion(horario.fecha(), horario.prestacionId()))
                .toList(), zonaHorariaClinica);

        Set<LocalDate> fechasAAgregar = horariosAAgregar.stream().map(HorarioAAgregarRequest::fecha).collect(Collectors.toSet());
        List<AgendaHorariosDia> horariosExistentes = agendaHorariosDiaDomainService
                .findByAgendaMedicoIdAndFechas(agendaMedico.getId(), fechasAAgregar);
        Map<LocalDate, List<GeneradorSlotsAgenda.RangoHorario>> rangosExistentesPorDia = horariosExistentes.stream()
                .collect(Collectors.groupingBy(AgendaHorariosDia::getFecha,
                        Collectors.mapping(horario -> new GeneradorSlotsAgenda.RangoHorario(horario.getHoraDesde(), horario.getHoraHasta()),
                                Collectors.toList())));

        List<GeneradorSlotsAgenda.BloqueAGenerar> bloquesAGenerar = horariosAAgregar.stream()
                .map(horario -> new GeneradorSlotsAgenda.BloqueAGenerar(horario.fecha(), horario.horaDesde(), horario.horaHasta(),
                        prestacionesPorId.get(horario.prestacionId()), horario.duracionTurno()))
                .toList();

        List<GeneradorSlotsAgenda.SlotGenerado> slotsGenerados = generadorSlotsAgenda.generar(bloquesAGenerar,
                clinica.getHorarioInicioAtencion(), clinica.getHorarioFinAtencion(), rangosExistentesPorDia, zonaHorariaClinica);

        validateTopeGeneracion(slotsGenerados.size());

        List<AgendaHorariosDia> horariosNuevos = slotsGenerados.stream()
                .map(slot -> construirAgendaHorariosDia(agendaMedico, slot))
                .toList();

        return agendaHorariosDiaDomainService.saveAllAgendaHorariosDia(horariosNuevos);

    }

    private <T> List<T> nullToEmpty(List<T> lista) {

        return lista != null ? lista : new ArrayList<>();

    }

    //endregion

}
