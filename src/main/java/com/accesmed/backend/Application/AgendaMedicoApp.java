package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.AgendaDia;
import com.accesmed.backend.Domain.AgendaHorarios;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaMedicoCriteria;
import com.accesmed.backend.Records.AgendaMedico.Request.BloqueHorarioRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.CreateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.DiaPatronRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.DiaSueltoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.HorarioAAgregarRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.UpdateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.UpdateVigenciaAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Response.CreateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.DiaAgendaResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.GetAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaHorarioResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.UpdateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.UpdateVigenciaAgendaMedicoResponse;
import com.accesmed.backend.Services.DomainServices.AgendaDiaDomainService;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDomainService;
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
import com.accesmed.backend.Services.QueryServices.AgendaHorariosQueryService;
import com.accesmed.backend.Services.QueryServices.AgendaMedicoQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.Utils.GeneradorSlotsAgenda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso de Agenda. Orquesta el flujo completo del agregado {@code AgendaMedico} /
 * {@code AgendaDia} / {@code AgendaHorarios}: alta con expansión de patrón, delta de
 * composición sobre los slots, movimiento del período de vigencia y los tres listados con
 * filtrado dinámico. Coordina médico, prestación, su estado vigente, la vigencia de
 * {@code MedicoPrestacion} y los parámetros de la clínica — toda la orquestación
 * multi-entidad vive acá, nunca en un {@code DomainService} (ver {@code ARQUITECTURA.md §5.0}).
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
    private final AgendaDiaDomainService agendaDiaDomainService;
    private final AgendaHorariosDomainService agendaHorariosDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final ClinicaDomainService clinicaDomainService;

    private final AgendaMedicoQueryService agendaMedicoQueryService;
    private final AgendaHorariosQueryService agendaHorariosQueryService;

    private final AgendaMedicoMapper agendaMedicoMapper;
    private final GeneradorSlotsAgenda generadorSlotsAgenda;

    //endregion

    //region ========== Métodos de escritura ==========

    /**
     * Crea un período de agenda nuevo, expandiendo el patrón semanal o los días sueltos a
     * {@code AgendaDia} + {@code AgendaHorarios}. El patrón no se persiste: se descarta
     * después de expandirlo.
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

        List<BloqueConFecha> bloquesConFecha = expandirBloques(createAgendaMedicoRequest);

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

        Map<LocalDate, List<GeneradorSlotsAgenda.SlotGenerado>> slotsPorFecha = slotsGenerados.stream()
                .collect(Collectors.groupingBy(GeneradorSlotsAgenda.SlotGenerado::fecha));

        List<AgendaDia> diasCreados = new ArrayList<>();
        List<AgendaHorarios> horariosAGuardar = new ArrayList<>();
        for (Map.Entry<LocalDate, List<GeneradorSlotsAgenda.SlotGenerado>> entrada : slotsPorFecha.entrySet()) {

            AgendaDia diaCreado = agendaDiaDomainService.findOrCreateAgendaDia(agendaMedicoGuardada, entrada.getKey());
            diasCreados.add(diaCreado);

            for (GeneradorSlotsAgenda.SlotGenerado slot : entrada.getValue()) {
                horariosAGuardar.add(construirAgendaHorarios(diaCreado, slot));
            }

        }

        List<AgendaHorarios> horariosGuardados = agendaHorariosDomainService.saveAllAgendaHorarios(horariosAGuardar);

        Map<UUID, List<AgendaHorarios>> horariosPorDia = horariosGuardados.stream()
                .collect(Collectors.groupingBy(horario -> horario.getAgendaDia().getId()));
        List<DiaAgendaResponse> diasResponse = agendaMedicoMapper.toDiaAgendaResponses(diasCreados, horariosPorDia);

        CreateAgendaMedicoResponse createAgendaMedicoResponse = agendaMedicoMapper.toCreateResponse(
                agendaMedicoGuardada, diasResponse, horariosGuardados.size());
        return createAgendaMedicoResponse;

    }

    /**
     * Aplica un delta de composición sobre los slots de una agenda: altas y bajas de
     * {@code AgendaDia}/{@code AgendaHorarios}. Orden de la transacción: resolver la unión
     * de bajas, validar la guarda restrictiva contra slots ocupados, aplicar las bajas,
     * aplicar las altas (con get-or-create del día) y, por último, dar de baja los días que
     * quedaron sin horarios activos.
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

        AgendaMedico agendaMedicoExistente = agendaMedicoDomainService.findAgendaMedicoById(id);

        List<UUID> diasAExcluirIds = nullToEmpty(updateAgendaMedicoRequest.diasAExcluir());
        List<UUID> horariosAExcluirIds = nullToEmpty(updateAgendaMedicoRequest.horariosAExcluir());
        List<HorarioAAgregarRequest> horariosAAgregar = nullToEmpty(updateAgendaMedicoRequest.horariosAAgregar());

        //Paso 2: resolver todo lo que el request daría de baja
        List<AgendaDia> diasAExcluir = agendaDiaDomainService.findAgendaDiasByIds(diasAExcluirIds);
        Set<UUID> diasAExcluirIdSet = diasAExcluir.stream().map(AgendaDia::getId).collect(Collectors.toSet());

        validateSinContradiccion(diasAExcluir, horariosAAgregar);

        List<AgendaHorarios> horariosDirectosAExcluir = agendaHorariosDomainService.findAgendaHorariosByIds(horariosAExcluirIds);
        List<AgendaHorarios> horariosArrastradosPorDias = agendaHorariosDomainService.findByAgendaDiaIds(diasAExcluirIdSet);

        List<AgendaHorarios> horariosAEliminarTotal = unirSinDuplicados(horariosDirectosAExcluir, horariosArrastradosPorDias);
        Set<UUID> idsAEliminarTotal = horariosAEliminarTotal.stream().map(AgendaHorarios::getId).collect(Collectors.toSet());

        //Paso 3: guarda restrictiva sobre la unión, antes de escribir nada
        agendaHorariosDomainService.validateSinOcupados(idsAEliminarTotal);

        //Paso 4: aplicar las bajas
        agendaHorariosDomainService.softDeleteAll(horariosAEliminarTotal, "Excluido por actualización de agenda");
        for (AgendaDia diaAExcluir : diasAExcluir) {
            agendaDiaDomainService.softDeleteAgendaDia(diaAExcluir, "Excluido por actualización de agenda");
        }

        //Paso 5: aplicar las altas, con get-or-create del día
        List<AgendaHorarios> horariosGuardados = aplicarHorariosAAgregar(agendaMedicoExistente, horariosAAgregar);

        //Paso 6: dar de baja los días que quedaron sin horarios activos, entre los tocados por horariosAExcluir
        int cantidadDiasDadosDeBajaAutomaticamente = darDeBajaDiasSinHorariosActivos(horariosDirectosAExcluir, diasAExcluirIdSet);

        UpdateAgendaMedicoResponse updateAgendaMedicoResponse = new UpdateAgendaMedicoResponse(id,
                horariosGuardados.size(), horariosAEliminarTotal.size(), diasAExcluir.size(), cantidadDiasDadosDeBajaAutomaticamente);
        return updateAgendaMedicoResponse;

    }

    /**
     * Actualiza el período de vigencia de una agenda: mover el inicio (solo si no arrancó),
     * adelantar el fin (restrictivo, con baja en cascada de los días posteriores) o
     * atrasarlo. El no solapamiento con otros períodos del médico lo valida igual que el
     * alta.
     *
     * @param updateVigenciaAgendaMedicoRequest {@code UpdateVigenciaAgendaMedicoRequest} nuevas
     *        fechas, incluyendo el id de la agenda (ya validado contra la ruta en el Controller)
     * @return {@code UpdateVigenciaAgendaMedicoResponse} la vigencia actualizada y la cantidad de
     *         días dados de baja al adelantar el fin
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

        int cantidadDiasDadosDeBaja = 0;
        boolean seAdelantaFin = updateVigenciaAgendaMedicoRequest.fechaHoraFinVigencia() != null
                && nuevoFin.isBefore(agendaMedicoExistente.getFechaHoraFinVigencia());

        if (seAdelantaFin) {

            List<AgendaDia> diasPosteriores = agendaDiaDomainService.findDiasPosteriores(id, nuevoFin.toLocalDate());
            Set<UUID> diasPosterioresIds = diasPosteriores.stream().map(AgendaDia::getId).collect(Collectors.toSet());
            List<AgendaHorarios> horariosArrastrados = agendaHorariosDomainService.findByAgendaDiaIds(diasPosterioresIds);
            Set<UUID> idsHorariosArrastrados = horariosArrastrados.stream().map(AgendaHorarios::getId).collect(Collectors.toSet());

            agendaHorariosDomainService.validateSinOcupados(idsHorariosArrastrados);

            agendaHorariosDomainService.softDeleteAll(horariosArrastrados, "Excluido por adelanto de fin de vigencia de agenda");
            for (AgendaDia diaPosterior : diasPosteriores) {
                agendaDiaDomainService.softDeleteAgendaDia(diaPosterior, "Excluido por adelanto de fin de vigencia de agenda");
            }
            cantidadDiasDadosDeBaja = diasPosteriores.size();

        }

        agendaMedicoExistente.setFechaHoraInicioVigencia(nuevoInicio);
        agendaMedicoExistente.setFechaHoraFinVigencia(nuevoFin);
        AgendaMedico agendaMedicoActualizada = agendaMedicoDomainService.saveAgendaMedico(agendaMedicoExistente);

        UpdateVigenciaAgendaMedicoResponse updateVigenciaAgendaMedicoResponse = new UpdateVigenciaAgendaMedicoResponse(
                agendaMedicoActualizada.getId(), agendaMedicoActualizada.getFechaHoraInicioVigencia(),
                agendaMedicoActualizada.getFechaHoraFinVigencia(), cantidadDiasDadosDeBaja);
        return updateVigenciaAgendaMedicoResponse;

    }

    //endregion

    //region ========== Métodos de lectura ==========

    /**
     * Lista agendas médicas según el criteria de filtrado dinámico proporcionado, con los
     * conteos de días y horarios activos de cada una.
     *
     * @param agendaMedicoCriteria {@code AgendaMedicoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListAgendaMedicoResponse>} página de agendas que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListAgendaMedicoResponse> findAgendas(AgendaMedicoCriteria agendaMedicoCriteria, Pageable pageable) {

        log.info("Listado de agendas médicas iniciado: criteria={}, page={}", agendaMedicoCriteria, pageable);

        Page<AgendaMedico> paginaAgendas = agendaMedicoQueryService.findByCriteria(agendaMedicoCriteria, pageable);

        PageResponse<ListAgendaMedicoResponse> pageResponse = PageResponse.from(paginaAgendas, agenda -> agendaMedicoMapper.toListResponse(
                agenda, agendaDiaDomainService.countDiasActivos(agenda.getId()), agendaHorariosDomainService.countActivosByAgendaMedico(agenda.getId())));
        return pageResponse;

    }

    /**
     * Busca la única agenda médica que cumple el criteria proporcionado, con sus días y
     * horarios activos expandidos.
     *
     * @param agendaMedicoCriteria {@code AgendaMedicoCriteria} filtros a aplicar
     * @return {@code GetAgendaMedicoResponse} la agenda encontrada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna agenda
     *         cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetAgendaMedicoResponse getAgendaMedico(AgendaMedicoCriteria agendaMedicoCriteria) {

        log.info("Búsqueda puntual de agenda médica iniciada: criteria={}", agendaMedicoCriteria);

        AgendaMedico agendaMedicoEncontrada = agendaMedicoQueryService.findOneByCriteria(agendaMedicoCriteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna agenda médica que cumpla el criteria: {}", agendaMedicoCriteria);
                    return new RecursoNoEncontradoException(getClass(), "AGENDA_MEDICO_NO_ENCONTRADA",
                            "No existe una agenda médica que cumpla el criteria proporcionado.");
                });

        List<AgendaDia> diasActivos = agendaDiaDomainService.findDiasActivos(agendaMedicoEncontrada.getId());
        Map<UUID, List<AgendaHorarios>> horariosPorDia = agendaHorariosDomainService
                .findByAgendaDiaIds(diasActivos.stream().map(AgendaDia::getId).collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(horario -> horario.getAgendaDia().getId()));
        List<DiaAgendaResponse> diasResponse = agendaMedicoMapper.toDiaAgendaResponses(diasActivos, horariosPorDia);

        GetAgendaMedicoResponse getAgendaMedicoResponse = agendaMedicoMapper.toGetResponse(agendaMedicoEncontrada, diasResponse);
        return getAgendaMedicoResponse;

    }

    /**
     * Lista los horarios de agenda del panel según el criteria proporcionado. Guarda fija:
     * solo horarios activos.
     *
     * @param agendaHorariosCriteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null}
     *        para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListAgendaHorarioResponse>} página de horarios que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListAgendaHorarioResponse> findHorariosAgenda(AgendaHorariosCriteria agendaHorariosCriteria, Pageable pageable) {

        log.info("Listado de horarios de agenda iniciado: criteria={}, page={}", agendaHorariosCriteria, pageable);

        Page<AgendaHorarios> paginaHorarios = agendaHorariosQueryService.findByCriteria(agendaHorariosCriteria, pageable);

        PageResponse<ListAgendaHorarioResponse> pageResponse = PageResponse.from(paginaHorarios, agendaMedicoMapper::toListHorarioResponse);
        return pageResponse;

    }

    /**
     * Lista los horarios disponibles para reservar, el único listado que consume el
     * chatbot. Guardas fijas: sin ocupar, dentro del plazo de reserva y dentro del
     * horizonte de anticipación configurado en la clínica.
     *
     * @param agendaHorariosCriteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null}
     *        para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListHorarioDisponibleResponse>} página de horarios disponibles
     */
    @Transactional(readOnly = true)
    public PageResponse<ListHorarioDisponibleResponse> findHorariosDisponibles(AgendaHorariosCriteria agendaHorariosCriteria, Pageable pageable) {

        log.info("Listado de horarios disponibles iniciado: criteria={}, page={}", agendaHorariosCriteria, pageable);

        Clinica clinica = clinicaDomainService.findClinica();
        Page<AgendaHorarios> paginaHorarios = agendaHorariosQueryService.findHorariosDisponibles(
                agendaHorariosCriteria, pageable, clinica.getDiasMaximosAnticipacionReserva());

        PageResponse<ListHorarioDisponibleResponse> pageResponse = PageResponse.from(paginaHorarios, agendaMedicoMapper::toListHorarioDisponibleResponse);
        return pageResponse;

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

    private List<BloqueConFecha> expandirBloques(CreateAgendaMedicoRequest createAgendaMedicoRequest) {

        LocalDate fechaInicio = createAgendaMedicoRequest.fechaHoraInicioVigencia().toLocalDate();
        LocalDate fechaFin = createAgendaMedicoRequest.fechaHoraFinVigencia().toLocalDate();
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

    private Map<UUID, Prestacion> resolvePrestacionesPublicadas(Set<UUID> prestacionIds) {

        Map<UUID, Prestacion> prestacionesPorId = new HashMap<>();
        List<String> errores = new ArrayList<>();

        for (UUID prestacionId : prestacionIds) {
            try {
                Prestacion prestacionActiva = prestacionDomainService.findPrestacionActivaById(prestacionId);
                EstadoPrestacion estadoVigente = historicoEstadoPrestacionDomainService.getEstadoVigente(prestacionId);
                if (estadoVigente != EstadoPrestacion.PUBLICADA) {
                    errores.add("La prestación " + prestacionId + " no está publicada.");
                    continue;
                }
                prestacionesPorId.put(prestacionId, prestacionActiva);
            } catch (RecursoNoEncontradoException excepcion) {
                errores.add("La prestación " + prestacionId + " no existe o está deshabilitada.");
            }
        }

        if (!errores.isEmpty()) {
            log.warn("No se pudo resolver el lote de prestaciones de la agenda: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

        return prestacionesPorId;

    }

    private void validateMedicoPrestacionVigenteEnFechas(UUID medicoId, List<FechaPrestacion> fechasPrestacion,
            ZoneId zonaHorariaClinica) {

        List<String> errores = fechasPrestacion.stream()
                .filter(fechaPrestacion -> !medicoPrestacionDomainService.existsVigenteEnFecha(
                        medicoId, fechaPrestacion.prestacionId(), fechaPrestacion.fecha().atStartOfDay(zonaHorariaClinica)))
                .map(fechaPrestacion -> "El médico no tiene la prestación " + fechaPrestacion.prestacionId()
                        + " vigente en la fecha " + fechaPrestacion.fecha() + ".")
                .distinct()
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

    private AgendaHorarios construirAgendaHorarios(AgendaDia agendaDia, GeneradorSlotsAgenda.SlotGenerado slot) {

        AgendaHorarios agendaHorariosNueva = new AgendaHorarios();
        agendaHorariosNueva.setAgendaDia(agendaDia);
        agendaHorariosNueva.setPrestacion(slot.prestacion());
        agendaHorariosNueva.setHoraDesde(slot.horaDesde());
        agendaHorariosNueva.setHoraHasta(slot.horaHasta());
        agendaHorariosNueva.setFechaLimiteReserva(slot.fechaLimiteReserva());
        agendaHorariosNueva.setEstaOcupada(false);
        return agendaHorariosNueva;

    }

    private void validateSinContradiccion(List<AgendaDia> diasAExcluir, List<HorarioAAgregarRequest> horariosAAgregar) {

        Set<LocalDate> fechasDiasAExcluir = diasAExcluir.stream().map(AgendaDia::getFecha).collect(Collectors.toSet());

        List<String> errores = horariosAAgregar.stream()
                .filter(horario -> fechasDiasAExcluir.contains(horario.fecha()))
                .map(horario -> "No se puede agregar un horario el " + horario.fecha()
                        + " y excluir ese mismo día en el mismo request.")
                .distinct()
                .toList();

        if (!errores.isEmpty()) {
            log.warn("No se pudo actualizar la agenda médica: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    private List<AgendaHorarios> unirSinDuplicados(List<AgendaHorarios> primeraLista, List<AgendaHorarios> segundaLista) {

        Map<UUID, AgendaHorarios> horariosPorId = new LinkedHashMap<>();
        primeraLista.forEach(horario -> horariosPorId.put(horario.getId(), horario));
        segundaLista.forEach(horario -> horariosPorId.put(horario.getId(), horario));
        return new ArrayList<>(horariosPorId.values());

    }

    private List<AgendaHorarios> aplicarHorariosAAgregar(AgendaMedico agendaMedico, List<HorarioAAgregarRequest> horariosAAgregar) {

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
        Map<LocalDate, AgendaDia> diasPorFecha = new HashMap<>();
        Map<LocalDate, List<GeneradorSlotsAgenda.RangoHorario>> rangosExistentesPorDia = new HashMap<>();
        for (LocalDate fecha : fechasAAgregar) {
            AgendaDia dia = agendaDiaDomainService.findOrCreateAgendaDia(agendaMedico, fecha);
            diasPorFecha.put(fecha, dia);
            List<GeneradorSlotsAgenda.RangoHorario> rangosActivos = agendaHorariosDomainService.findByAgendaDiaId(dia.getId()).stream()
                    .map(horario -> new GeneradorSlotsAgenda.RangoHorario(horario.getHoraDesde(), horario.getHoraHasta()))
                    .toList();
            rangosExistentesPorDia.put(fecha, rangosActivos);
        }

        List<GeneradorSlotsAgenda.BloqueAGenerar> bloquesAGenerar = horariosAAgregar.stream()
                .map(horario -> new GeneradorSlotsAgenda.BloqueAGenerar(horario.fecha(), horario.horaDesde(), horario.horaHasta(),
                        prestacionesPorId.get(horario.prestacionId()), horario.duracionTurno()))
                .toList();

        List<GeneradorSlotsAgenda.SlotGenerado> slotsGenerados = generadorSlotsAgenda.generar(bloquesAGenerar,
                clinica.getHorarioInicioAtencion(), clinica.getHorarioFinAtencion(), rangosExistentesPorDia, zonaHorariaClinica);

        validateTopeGeneracion(slotsGenerados.size());

        List<AgendaHorarios> horariosNuevos = slotsGenerados.stream()
                .map(slot -> construirAgendaHorarios(diasPorFecha.get(slot.fecha()), slot))
                .toList();

        return agendaHorariosDomainService.saveAllAgendaHorarios(horariosNuevos);

    }

    private int darDeBajaDiasSinHorariosActivos(List<AgendaHorarios> horariosDirectosAExcluir, Set<UUID> diasYaExcluidos) {

        Set<UUID> diasAfectados = horariosDirectosAExcluir.stream()
                .map(horario -> horario.getAgendaDia().getId())
                .filter(diaId -> !diasYaExcluidos.contains(diaId))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int cantidadDiasDadosDeBaja = 0;
        for (UUID diaId : diasAfectados) {
            if (agendaHorariosDomainService.findByAgendaDiaId(diaId).isEmpty()) {
                AgendaDia diaSinHorarios = agendaDiaDomainService.findAgendaDiaActivoById(diaId);
                agendaDiaDomainService.softDeleteAgendaDia(diaSinHorarios, "Sin horarios activos tras actualización de agenda");
                cantidadDiasDadosDeBaja++;
            }
        }

        return cantidadDiasDadosDeBaja;

    }

    private <T> List<T> nullToEmpty(List<T> lista) {

        return lista != null ? lista : new ArrayList<>();

    }

    //endregion

}
