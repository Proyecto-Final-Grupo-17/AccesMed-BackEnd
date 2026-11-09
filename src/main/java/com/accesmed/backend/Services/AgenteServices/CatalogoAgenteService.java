package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.Agente.Criteria.DiaConAgendaProyeccion;
import com.accesmed.backend.Records.Agente.Criteria.HorarioAgenteProyeccion;
import com.accesmed.backend.Records.Agente.Criteria.RangoAtencionMedicoProyeccion;
import com.accesmed.backend.Records.Agente.Criteria.SearchHorarioAgenteCriteria;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Request.SearchHorarioAgenteRequest;
import com.accesmed.backend.Records.Agente.Response.DiaAgendaAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListHorarioDisponibleAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchHorarioAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetOfertaEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListIndicacionPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchPrestacionAgenteResponse;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.AgenteMapper;
import com.accesmed.backend.Services.QueryServices.AgendaHorariosDiaQueryService;
import com.accesmed.backend.Services.Utils.CoincidenciaBusqueda;
import com.accesmed.backend.Services.Utils.FormatoMensaje;
import com.accesmed.backend.Services.Utils.NormalizadorBusqueda;
import com.accesmed.backend.Services.Utils.ResultadoBusqueda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo del canal chatbot (capa {@code Services/AgenteServices}, {@code Docs/Diseno-Chatbot.md
 * §3.3}): resuelve especialidades, prestaciones y médicos por parecido con lo que escribió el
 * paciente y arma las respuestas con la relación {@code MedicoPrestacion} vigente (no todos los
 * médicos de una especialidad hacen todas sus prestaciones) y las indicaciones vigentes.
 * <p>
 * Solo ofrece lo que se puede reservar: prestaciones {@code PUBLICADA}, médicos activos y
 * asignaciones vigentes hoy. Además resuelve las búsquedas flexibles por parecido ({@code searchMedicos}
 * con especialidad y prestación, {@code searchHorarios} con médico, prestación, especialidad, día y hora).
 * La usan únicamente {@code AgenteApp} y {@code ChatwootApp}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoAgenteService {

    //region ========== Constantes ==========

    private static final int LIMITE_RESULTADOS = 10;

    /**
     * Días que se piden por página en la búsqueda de horarios.
     */
    private static final int TOPE_DIAS_HORARIOS = 3;

    /**
     * Horarios que se listan por prestación (de un médico en un día) en la búsqueda de horarios.
     */
    private static final int TOPE_HORARIOS_PRESTACION = 10;

    /**
     * Días hacia adelante (cuatro semanas) en los que se buscan los días con agenda de un médico.
     */
    private static final int DIAS_AGENDA_MEDICO = 28;

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadDomainService especialidadDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final MedicoDomainService medicoDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final ClinicaDomainService clinicaDomainService;
    private final AgendaHorariosDiaQueryService agendaHorariosDiaQueryService;
    private final AgenteMapper agenteMapper;

    /**
     * Similitud mínima de trigramas para el fallback de las búsquedas por parecido (Diseno-Chatbot §4.5).
     */
    @Value("${accesmed.agente.busqueda.umbral-similitud:0.5}")
    private double umbralSimilitud;

    //endregion

    //region ========== Métodos ==========

    /**
     * Resuelve una especialidad activa por parecido con el nombre ("cardio" → Cardiología). Si una
     * coincidencia es exacta (sin mayúsculas ni tildes) gana sobre las demás.
     *
     * @param nombre {@code String} nombre aproximado de la especialidad
     * @return {@code Especialidad} la única especialidad que coincide
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no coincide ninguna
     * @throws ReglaNegocioException {@code ReglaNegocioException} si coinciden varias
     */
    public Especialidad findEspecialidadByNombreAproximado(String nombre) {

        List<Especialidad> especialidadesParecidas = especialidadDomainService
                .searchEspecialidadesActivasByNombre(nombre, umbralSimilitud).resultados();

        //Ninguna parecida
        if (especialidadesParecidas.isEmpty()) {
            log.warn("No se encontró una especialidad parecida a: {}", nombre);
            throw new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                    "No encontramos la especialidad \"" + nombre + "\". Pedí el listado de especialidades.");
        }

        //Una sola, o una exacta entre varias
        Especialidad especialidadElegida = findUnicaOExacta(especialidadesParecidas, Especialidad::getNombre, nombre);
        if (especialidadElegida == null) {
            List<String> opciones = especialidadesParecidas.stream().map(Especialidad::getNombre).toList();
            log.warn("Especialidad ambigua: {} coincide con {}", nombre, opciones);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_AMBIGUA",
                    "Hay varias especialidades parecidas a \"" + nombre + "\": " + String.join(", ", opciones)
                            + ". Preguntale al paciente cuál.");
        }

        return especialidadElegida;

    }

    /**
     * Arma la oferta de una especialidad: sus prestaciones publicadas, cada una con los médicos que
     * la atienden hoy. Las prestaciones sin médicos se omiten (no se pueden reservar).
     *
     * @param especialidad {@code Especialidad} especialidad activa
     * @return {@code GetOfertaEspecialidadAgenteResponse} oferta con la relación prestación ↔ médico
     */
    public GetOfertaEspecialidadAgenteResponse buildOfertaEspecialidad(Especialidad especialidad) {

        LocalDate hoy = LocalDate.now();

        List<ListPrestacionAgenteResponse> prestacionesOfrecidas = prestacionDomainService
                .findPrestacionesPublicadasByEspecialidadId(especialidad.getId())
                .stream()
                .map(prestacion -> agenteMapper.toListPrestacionResponse(prestacion,
                        isValidacionRequerida(prestacion.getId()), findMedicosQueAtienden(prestacion.getId(), hoy)))
                .filter(listPrestacionAgenteResponse -> !listPrestacionAgenteResponse.medicos().isEmpty())
                .toList();

        GetOfertaEspecialidadAgenteResponse getOfertaEspecialidadAgenteResponse =
                agenteMapper.toGetOfertaEspecialidadResponse(especialidad.getNombre(), prestacionesOfrecidas);
        return getOfertaEspecialidadAgenteResponse;

    }

    /**
     * Busca prestaciones publicadas por parecido con el nombre, con su especialidad, sus
     * indicaciones vigentes y los médicos que las atienden hoy.
     *
     * @param nombre {@code String} nombre aproximado de la prestación
     * @return {@code List<SearchPrestacionAgenteResponse>} hasta 10 coincidencias (vacía si no hay)
     */
    public List<SearchPrestacionAgenteResponse> searchPrestaciones(String nombre) {

        LocalDate hoy = LocalDate.now();

        ResultadoBusqueda<Prestacion> resultadoBusqueda = prestacionDomainService.searchPrestacionesByNombre(nombre, umbralSimilitud);

        List<SearchPrestacionAgenteResponse> searchPrestacionAgenteResponses = filterPublicadas(resultadoBusqueda.resultados())
                .stream()
                .limit(LIMITE_RESULTADOS)
                .map(prestacion -> {
                    List<IndicacionPrestacion> indicacionesVigentes =
                            indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(prestacion.getId());
                    return agenteMapper.toSearchPrestacionResponse(prestacion,
                            indicacionesVigentes.stream().anyMatch(IndicacionPrestacion::getRequiereValidacion),
                            agenteMapper.toListIndicacionPrestacionResponses(indicacionesVigentes),
                            findMedicosQueAtienden(prestacion.getId(), hoy), resultadoBusqueda.coincidencia());
                })
                .toList();
        return searchPrestacionAgenteResponses;

    }

    /**
     * Busca médicos activos por parecido con su nombre o apellido, por especialidad o por prestación (los
     * tres filtros son opcionales, pero hace falta al menos uno). La especialidad filtra; la prestación solo
     * filtra si no se nombró al médico ni la especialidad: si se nombró, se informa por médico si la hace
     * ({@code haceLaPrestacion}) y si la atiende particular. Cada médico trae sus prestaciones publicadas de
     * hoy y los días en que tiene agenda en las próximas semanas.
     *
     * @param nombre {@code String} nombre o apellido aproximado del médico, o {@code null}
     * @param especialidad {@code String} nombre aproximado de la especialidad, o {@code null}
     * @param prestacion {@code String} nombre aproximado de la prestación, o {@code null}
     * @return {@code List<SearchMedicoAgenteResponse>} hasta 10 coincidencias (vacía si no hay)
     */
    public List<SearchMedicoAgenteResponse> searchMedicos(String nombre, String especialidad, String prestacion) {

        LocalDate hoy = LocalDate.now();

        //Prestaciones publicadas pedidas (si se pidió una que no existe, no hay médicos que la hagan)
        List<Prestacion> prestacionesPedidas = null;
        if (hasTexto(prestacion)) {
            prestacionesPedidas = filterPublicadas(prestacionDomainService.searchPrestacionesByNombre(prestacion, umbralSimilitud).resultados())
                    .stream().limit(LIMITE_RESULTADOS).toList();
            if (prestacionesPedidas.isEmpty()) {
                return List.of();
            }
        }

        //Especialidades pedidas
        Set<UUID> especialidadIds = null;
        if (hasTexto(especialidad)) {
            especialidadIds = especialidadDomainService.searchEspecialidadesActivasByNombre(especialidad, umbralSimilitud)
                    .resultados().stream().map(Especialidad::getId).collect(Collectors.toSet());
            if (especialidadIds.isEmpty()) {
                return List.of();
            }
        }

        //Médicos candidatos: por nombre; si no, por quienes hacen la prestación; si no, por especialidad
        CoincidenciaBusqueda coincidencia = CoincidenciaBusqueda.EXACTA;
        List<Medico> medicosCandidatos;
        if (hasTexto(nombre)) {
            ResultadoBusqueda<Medico> resultadoBusqueda = medicoDomainService.searchMedicosActivosByNombre(nombre, umbralSimilitud);
            medicosCandidatos = resultadoBusqueda.resultados();
            coincidencia = resultadoBusqueda.coincidencia();
        } else if (prestacionesPedidas != null) {
            medicosCandidatos = prestacionesPedidas.stream()
                    .flatMap(prestacionPedida -> medicoPrestacionDomainService
                            .findAsignacionesVigentesByPrestacion(prestacionPedida.getId(), hoy).stream())
                    .map(MedicoPrestacion::getMedico)
                    .filter(medico -> medico.getDeletedAt() == null)
                    .distinct()
                    .sorted(Comparator.comparing(Medico::getApellido).thenComparing(Medico::getNombre))
                    .toList();
        } else if (especialidadIds != null) {
            medicosCandidatos = especialidadIds.stream()
                    .flatMap(especialidadId -> medicoDomainService.findMedicosActivosByEspecialidadId(especialidadId).stream())
                    .toList();
        } else {
            return List.of();
        }

        //La especialidad filtra siempre
        Set<UUID> especialidadesPermitidas = especialidadIds;
        List<Medico> medicosEncontrados = medicosCandidatos.stream()
                .filter(medico -> especialidadesPermitidas == null
                        || especialidadesPermitidas.contains(medico.getEspecialidad().getId()))
                .limit(LIMITE_RESULTADOS)
                .toList();
        if (medicosEncontrados.isEmpty()) {
            return List.of();
        }

        //Días con agenda de todos los médicos en una sola consulta (sin N+1)
        Set<UUID> prestacionPedidaIds = prestacionesPedidas == null ? null
                : prestacionesPedidas.stream().map(Prestacion::getId).collect(Collectors.toSet());
        Map<UUID, List<DiaAgendaAgenteResponse>> diasConAgendaPorMedico = agendaHorariosDiaQueryService
                .findDiasConAgendaByMedicos(medicosEncontrados.stream().map(Medico::getId).toList(), prestacionPedidaIds,
                        hoy, hoy.plusDays(DIAS_AGENDA_MEDICO))
                .stream()
                .collect(Collectors.groupingBy(DiaConAgendaProyeccion::medicoId, Collectors.mapping(
                        diaConAgenda -> new DiaAgendaAgenteResponse(diaConAgenda.fecha(), buildFechaTexto(diaConAgenda.fecha()),
                                diaConAgenda.horariosLibres() > 0), Collectors.toList())));

        CoincidenciaBusqueda coincidenciaEncontrada = coincidencia;
        List<SearchMedicoAgenteResponse> searchMedicoAgenteResponses = medicosEncontrados.stream()
                .map(medico -> {
                    //Qué de lo pedido hace este médico hoy y si lo atiende particular
                    Boolean haceLaPrestacion = null;
                    Boolean atiendeParticular = null;
                    if (prestacionPedidaIds != null) {
                        List<MedicoPrestacion> asignacionesPedidas = medicoPrestacionDomainService
                                .findAsignacionesVigentesByMedico(medico.getId(), hoy).stream()
                                .filter(asignacion -> prestacionPedidaIds.contains(asignacion.getPrestacion().getId()))
                                .toList();
                        haceLaPrestacion = !asignacionesPedidas.isEmpty();
                        atiendeParticular = asignacionesPedidas.stream().anyMatch(MedicoPrestacion::getAtiendeParticular);
                    }
                    return agenteMapper.toSearchMedicoResponse(medico,
                            findPrestacionesQueAtiende(medico.getId(), hoy).stream().map(Prestacion::getNombre).toList(),
                            coincidenciaEncontrada, haceLaPrestacion, atiendeParticular,
                            diasConAgendaPorMedico.getOrDefault(medico.getId(), List.of()));
                })
                .toList();
        return searchMedicoAgenteResponses;

    }

    /**
     * Busca los horarios disponibles según filtros opcionales (médico, prestación, especialidad, día,
     * franja y hora; al menos uno de médico, prestación o especialidad) y los agrupa por día, médico y
     * prestación. Los nombres se resuelven por parecido. La consulta exige lo mismo que la reserva
     * (horario libre, dentro del plazo y del horizonte, médico activo, prestación publicada y asignación
     * vigente en la fecha). Trae como máximo {@value #TOPE_DIAS_HORARIOS} días y
     * {@value #TOPE_HORARIOS_PRESTACION} horarios por prestación, con {@code hayMas} para pedir otra página.
     * Si no hay horarios, explica por qué ({@code codigoMotivo} y {@code motivo}).
     *
     * @param searchHorarioAgenteRequest {@code SearchHorarioAgenteRequest} filtros de la búsqueda
     * @return {@code SearchHorarioAgenteResponse} horarios agrupados, o el motivo por el que no hay
     */
    public SearchHorarioAgenteResponse searchHorarios(SearchHorarioAgenteRequest searchHorarioAgenteRequest) {

        LocalDate hoy = LocalDate.now();
        LocalDate horizonte = hoy.plusDays(clinicaDomainService.findClinica().getDiasMaximosAnticipacionReserva());

        //Resolver por parecido lo que nombró el paciente (si nombró algo que no existe, no hay nada que buscar)
        Set<UUID> medicoIds = null;
        if (hasTexto(searchHorarioAgenteRequest.medico())) {
            medicoIds = resolveMedicoIds(searchHorarioAgenteRequest.medico());
            if (medicoIds.isEmpty()) {
                return buildHorariosSinResultados("NO_ENCONTRADO",
                        "No encontramos un médico llamado \"" + searchHorarioAgenteRequest.medico() + "\".");
            }
        }
        Set<UUID> prestacionIds = null;
        if (hasTexto(searchHorarioAgenteRequest.prestacion())) {
            prestacionIds = filterPublicadas(prestacionDomainService
                    .searchPrestacionesByNombre(searchHorarioAgenteRequest.prestacion(), umbralSimilitud).resultados())
                    .stream().limit(LIMITE_RESULTADOS).map(Prestacion::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            if (prestacionIds.isEmpty()) {
                return buildHorariosSinResultados("NO_ENCONTRADO",
                        "No encontramos la prestación \"" + searchHorarioAgenteRequest.prestacion() + "\".");
            }
        }
        Set<UUID> especialidadIds = null;
        if (hasTexto(searchHorarioAgenteRequest.especialidad())) {
            especialidadIds = especialidadDomainService
                    .searchEspecialidadesActivasByNombre(searchHorarioAgenteRequest.especialidad(), umbralSimilitud)
                    .resultados().stream().map(Especialidad::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            if (especialidadIds.isEmpty()) {
                return buildHorariosSinResultados("NO_ENCONTRADO",
                        "No encontramos la especialidad \"" + searchHorarioAgenteRequest.especialidad() + "\".");
            }
        }

        //Días: un día puntual pisa el rango; "los jueves" se expande a las fechas del período dentro del horizonte
        LocalDate fechaDesde = searchHorarioAgenteRequest.fecha() != null ? searchHorarioAgenteRequest.fecha() : searchHorarioAgenteRequest.fechaDesde();
        LocalDate fechaHasta = searchHorarioAgenteRequest.fecha() != null ? searchHorarioAgenteRequest.fecha() : searchHorarioAgenteRequest.fechaHasta();
        if (fechaDesde != null && fechaDesde.isAfter(horizonte)) {
            return buildHorariosSinResultados("FUERA_DE_HORIZONTE", "Solo se pueden reservar turnos con hasta "
                    + ChronoUnit.DAYS.between(hoy, horizonte) + " días de anticipación (hasta el " + FormatoMensaje.fecha(horizonte) + ").");
        }
        List<LocalDate> fechasDiaSemana = null;
        if (searchHorarioAgenteRequest.diaSemana() != null) {
            LocalDate inicio = fechaDesde != null && fechaDesde.isAfter(hoy) ? fechaDesde : hoy;
            LocalDate fin = fechaHasta != null && fechaHasta.isBefore(horizonte) ? fechaHasta : horizonte;
            fechasDiaSemana = inicio.isAfter(fin) ? List.of() : inicio.datesUntil(fin.plusDays(1))
                    .filter(fecha -> fecha.getDayOfWeek() == searchHorarioAgenteRequest.diaSemana().dayOfWeek())
                    .toList();
        }

        //Hora de inicio: la franja y el rango de horas se intersectan
        FranjaHorariaAgente franjaHoraria = searchHorarioAgenteRequest.franjaHoraria();
        LocalTime horaDesdeMinima = maxHora(franjaHoraria != null ? franjaHoraria.desde() : null, searchHorarioAgenteRequest.horaDesde());
        LocalTime horaDesdeMaxima = minHora(franjaHoraria != null ? franjaHoraria.hasta() : null, searchHorarioAgenteRequest.horaHasta());

        SearchHorarioAgenteCriteria searchHorarioAgenteCriteria = new SearchHorarioAgenteCriteria(medicoIds, prestacionIds,
                especialidadIds, fechaDesde, fechaHasta, fechasDiaSemana, horaDesdeMinima, horaDesdeMaxima);

        //Paginar por día (un día de más para saber si hay otra página)
        int pagina = searchHorarioAgenteRequest.pagina() != null && searchHorarioAgenteRequest.pagina() > 0
                ? searchHorarioAgenteRequest.pagina() : 0;
        List<LocalDate> fechasConHorarios = agendaHorariosDiaQueryService.findFechasConHorariosDisponibles(
                searchHorarioAgenteCriteria, pagina * TOPE_DIAS_HORARIOS, TOPE_DIAS_HORARIOS + 1);
        if (fechasConHorarios.isEmpty()) {
            return buildHorariosSinResultados(searchHorarioAgenteCriteria, medicoIds, prestacionIds, hoy);
        }
        boolean hayMasDias = fechasConHorarios.size() > TOPE_DIAS_HORARIOS;
        List<LocalDate> fechasPagina = hayMasDias ? fechasConHorarios.subList(0, TOPE_DIAS_HORARIOS) : fechasConHorarios;

        //Horarios de esos días y rango en que atiende cada médico (dos consultas en total)
        List<HorarioAgenteProyeccion> horariosDisponibles = agendaHorariosDiaQueryService
                .findHorariosDisponiblesByCriteriaAgente(searchHorarioAgenteCriteria.withFechas(fechasPagina));
        Map<String, RangoAtencionMedicoProyeccion> rangosAtencion = agendaHorariosDiaQueryService
                .findRangosAtencionMedicos(horariosDisponibles.stream().map(HorarioAgenteProyeccion::medicoId).distinct().toList(), fechasPagina)
                .stream()
                .collect(Collectors.toMap(rango -> rango.medicoId() + "|" + rango.fecha(), Function.identity()));

        //Agrupar día → médico → prestación (la consulta ya viene ordenada)
        boolean hayMasHorarios = false;
        List<SearchHorarioAgenteResponse.DiaHorario> diasHorarios = new ArrayList<>();
        Map<LocalDate, List<HorarioAgenteProyeccion>> horariosPorDia = horariosDisponibles.stream()
                .collect(Collectors.groupingBy(HorarioAgenteProyeccion::fecha, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<LocalDate, List<HorarioAgenteProyeccion>> horariosDelDia : horariosPorDia.entrySet()) {
            List<SearchHorarioAgenteResponse.MedicoHorario> medicosHorarios = new ArrayList<>();
            Map<UUID, List<HorarioAgenteProyeccion>> horariosPorMedico = horariosDelDia.getValue().stream()
                    .collect(Collectors.groupingBy(HorarioAgenteProyeccion::medicoId, LinkedHashMap::new, Collectors.toList()));
            for (List<HorarioAgenteProyeccion> horariosDelMedico : horariosPorMedico.values()) {
                HorarioAgenteProyeccion primerHorario = horariosDelMedico.getFirst();
                Map<UUID, List<HorarioAgenteProyeccion>> horariosPorPrestacion = horariosDelMedico.stream()
                        .collect(Collectors.groupingBy(HorarioAgenteProyeccion::prestacionId, LinkedHashMap::new, Collectors.toList()));
                List<SearchHorarioAgenteResponse.PrestacionHorario> prestacionesHorarios = new ArrayList<>();
                for (List<HorarioAgenteProyeccion> horariosDeLaPrestacion : horariosPorPrestacion.values()) {
                    boolean hayMasEnLaPrestacion = horariosDeLaPrestacion.size() > TOPE_HORARIOS_PRESTACION;
                    hayMasHorarios = hayMasHorarios || hayMasEnLaPrestacion;
                    prestacionesHorarios.add(new SearchHorarioAgenteResponse.PrestacionHorario(
                            horariosDeLaPrestacion.getFirst().nombrePrestacion(),
                            horariosDeLaPrestacion.stream().limit(TOPE_HORARIOS_PRESTACION).map(this::toHorarioResponse).toList(),
                            hayMasEnLaPrestacion));
                }
                RangoAtencionMedicoProyeccion rangoAtencion = rangosAtencion.get(primerHorario.medicoId() + "|" + primerHorario.fecha());
                medicosHorarios.add(new SearchHorarioAgenteResponse.MedicoHorario(primerHorario.nombreMedico(),
                        primerHorario.apellidoMedico(), primerHorario.matricula(), primerHorario.especialidad(),
                        rangoAtencion != null ? rangoAtencion.desde() : null,
                        rangoAtencion != null ? rangoAtencion.hasta() : null, prestacionesHorarios));
            }
            diasHorarios.add(new SearchHorarioAgenteResponse.DiaHorario(horariosDelDia.getKey(),
                    buildFechaTexto(horariosDelDia.getKey()), medicosHorarios));
        }

        SearchHorarioAgenteResponse searchHorarioAgenteResponse = new SearchHorarioAgenteResponse(diasHorarios,
                hayMasDias || hayMasHorarios, null, null);
        return searchHorarioAgenteResponse;

    }

    /**
     * Resuelve, por parecido con el nombre, una prestación publicada (para consultar su cobertura).
     *
     * @param nombrePrestacion {@code String} nombre aproximado de la prestación
     * @return {@code Prestacion} la única prestación publicada que coincide (o la exacta entre varias)
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna prestación publicada se parece al nombre
     * @throws ReglaNegocioException {@code ReglaNegocioException} si coinciden varias
     */
    public Prestacion findPrestacionPublicadaByNombreAproximado(String nombrePrestacion) {

        List<Prestacion> prestacionesParecidas = filterPublicadas(
                prestacionDomainService.searchPrestacionesByNombre(nombrePrestacion, umbralSimilitud).resultados());
        if (prestacionesParecidas.isEmpty()) {
            log.warn("No se encontró una prestación publicada parecida a: {}", nombrePrestacion);
            throw new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                    "No encontramos la prestación \"" + nombrePrestacion + "\".");
        }

        Prestacion prestacionElegida = findUnicaOExacta(prestacionesParecidas, Prestacion::getNombre, nombrePrestacion);
        if (prestacionElegida == null) {
            List<String> opciones = prestacionesParecidas.stream().map(Prestacion::getNombre).toList();
            log.warn("Prestación ambigua: {} coincide con {}", nombrePrestacion, opciones);
            throw new ReglaNegocioException(getClass(), "PRESTACION_AMBIGUA",
                    "Hay varias prestaciones parecidas a \"" + nombrePrestacion + "\": " + String.join(", ", opciones)
                            + ". Preguntale al paciente cuál.");
        }
        return prestacionElegida;

    }

    /**
     * Resuelve un médico activo por su matrícula o, si no coincide, por parecido con su nombre o apellido.
     *
     * @param medico {@code String} matrícula, o nombre o apellido aproximado
     * @return {@code Medico} el único médico activo que coincide
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no coincide ninguno
     * @throws ReglaNegocioException {@code ReglaNegocioException} si coinciden varios
     */
    public Medico findMedicoActivoByMatriculaONombreAproximado(String medico) {

        Optional<Medico> medicoPorMatricula = medicoDomainService.findOptionalMedicoActivoByMatricula(medico.trim());
        if (medicoPorMatricula.isPresent()) {
            return medicoPorMatricula.get();
        }

        List<Medico> medicosParecidos = medicoDomainService.searchMedicosActivosByNombre(medico, umbralSimilitud).resultados();
        if (medicosParecidos.isEmpty()) {
            log.warn("No se encontró un médico parecido a: {}", medico);
            throw new RecursoNoEncontradoException(getClass(), "MEDICO_NO_ENCONTRADO",
                    "No encontramos un médico llamado \"" + medico + "\".");
        }
        if (medicosParecidos.size() > 1) {
            List<String> opciones = medicosParecidos.stream().limit(LIMITE_RESULTADOS)
                    .map(medicoParecido -> medicoParecido.getNombre() + " " + medicoParecido.getApellido()).toList();
            log.warn("Médico ambiguo: {} coincide con {}", medico, opciones);
            throw new ReglaNegocioException(getClass(), "MEDICO_AMBIGUO",
                    "Hay varios médicos parecidos a \"" + medico + "\": " + String.join(", ", opciones)
                            + ". Preguntale al paciente cuál.");
        }
        return medicosParecidos.getFirst();

    }

    /**
     * Resuelve, por parecido con el nombre, una de las prestaciones publicadas que el médico atiende
     * hoy (para consultar sus horarios).
     *
     * @param nombrePrestacion {@code String} nombre aproximado de la prestación
     * @param medico {@code Medico} médico activo
     * @return {@code Prestacion} la única prestación del médico que coincide
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna prestación publicada se parece al nombre
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el médico no atiende ninguna
     *         prestación parecida, o si atiende varias
     */
    public Prestacion findPrestacionDeMedicoByNombreAproximado(String nombrePrestacion, Medico medico) {

        //Prestaciones publicadas parecidas al nombre (en toda la clínica)
        List<Prestacion> prestacionesParecidas = filterPublicadas(
                prestacionDomainService.searchPrestacionesByNombre(nombrePrestacion, umbralSimilitud).resultados());
        if (prestacionesParecidas.isEmpty()) {
            log.warn("No se encontró una prestación publicada parecida a: {}", nombrePrestacion);
            throw new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                    "No encontramos la prestación \"" + nombrePrestacion + "\".");
        }

        //Quedarse con las que el médico atiende hoy
        Set<UUID> prestacionesDelMedico = findPrestacionesQueAtiende(medico.getId(), LocalDate.now()).stream()
                .map(Prestacion::getId)
                .collect(Collectors.toSet());
        List<Prestacion> prestacionesDelMedicoParecidas = prestacionesParecidas.stream()
                .filter(prestacion -> prestacionesDelMedico.contains(prestacion.getId()))
                .toList();
        if (prestacionesDelMedicoParecidas.isEmpty()) {
            log.warn("El médico {} no atiende una prestación parecida a {}", medico.getId(), nombrePrestacion);
            throw new ReglaNegocioException(getClass(), "MEDICO_NO_ATIENDE_PRESTACION",
                    "El médico elegido no atiende esa prestación.");
        }

        //Una sola, o una exacta entre varias
        Prestacion prestacionElegida = findUnicaOExacta(prestacionesDelMedicoParecidas, Prestacion::getNombre, nombrePrestacion);
        if (prestacionElegida == null) {
            List<String> opciones = prestacionesDelMedicoParecidas.stream().map(Prestacion::getNombre).toList();
            log.warn("Prestación ambigua para el médico {}: {} coincide con {}", medico.getId(), nombrePrestacion, opciones);
            throw new ReglaNegocioException(getClass(), "PRESTACION_AMBIGUA",
                    "Hay varias prestaciones parecidas a \"" + nombrePrestacion + "\": " + String.join(", ", opciones)
                            + ". Preguntale al paciente cuál.");
        }

        return prestacionElegida;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Resuelve el médico de un filtro por su matrícula o, si no coincide, por parecido con su nombre o apellido.
     *
     * @param medico {@code String} matrícula, o nombre o apellido aproximado
     * @return {@code Set<UUID>} hasta 10 médicos activos que coinciden (vacío si ninguno)
     */
    private Set<UUID> resolveMedicoIds(String medico) {

        Optional<Medico> medicoPorMatricula = medicoDomainService.findOptionalMedicoActivoByMatricula(medico.trim());
        if (medicoPorMatricula.isPresent()) {
            return Set.of(medicoPorMatricula.get().getId());
        }

        Set<UUID> medicoIds = medicoDomainService.searchMedicosActivosByNombre(medico, umbralSimilitud).resultados().stream()
                .limit(LIMITE_RESULTADOS)
                .map(Medico::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return medicoIds;

    }

    /**
     * Arma la respuesta de la búsqueda de horarios cuando no hay ninguno y se sabe por qué sin consultar más.
     *
     * @param codigoMotivo {@code String} código del motivo
     * @param motivo {@code String} motivo en español para el paciente
     * @return {@code SearchHorarioAgenteResponse} respuesta sin días y con el motivo
     */
    private SearchHorarioAgenteResponse buildHorariosSinResultados(String codigoMotivo, String motivo) {

        SearchHorarioAgenteResponse searchHorarioAgenteResponse = new SearchHorarioAgenteResponse(List.of(), false,
                codigoMotivo, motivo);
        return searchHorarioAgenteResponse;

    }

    /**
     * Explica por qué no hay horarios disponibles para los criterios: el médico no hace la prestación, hay
     * horarios pero todos están ocupados (o vencieron su plazo) o no hay horarios cargados en ese día o franja.
     *
     * @param searchHorarioAgenteCriteria {@code SearchHorarioAgenteCriteria} filtros de la búsqueda
     * @param medicoIds {@code Set<UUID>} médicos resueltos, o {@code null} si no se filtró por médico
     * @param prestacionIds {@code Set<UUID>} prestaciones resueltas, o {@code null} si no se filtró por prestación
     * @param hoy {@code LocalDate} día de hoy
     * @return {@code SearchHorarioAgenteResponse} respuesta sin días y con el motivo
     */
    private SearchHorarioAgenteResponse buildHorariosSinResultados(SearchHorarioAgenteCriteria searchHorarioAgenteCriteria,
            Set<UUID> medicoIds, Set<UUID> prestacionIds, LocalDate hoy) {

        //Médico y prestación nombrados: tiene que haber alguna combinación que el médico atienda hoy
        if (medicoIds != null && prestacionIds != null) {
            boolean algunaCombinacionVigente = medicoIds.stream().anyMatch(medicoId ->
                    medicoPrestacionDomainService.findAsignacionesVigentesByMedico(medicoId, hoy).stream()
                            .anyMatch(asignacion -> prestacionIds.contains(asignacion.getPrestacion().getId())));
            if (!algunaCombinacionVigente) {
                return buildHorariosSinResultados("NO_HACE_PRESTACION", "El médico no atiende esa prestación.");
            }
        }

        //Hay horarios cargados con esos filtros pero no se pueden reservar
        if (agendaHorariosDiaQueryService.existsHorariosByCriteriaAgente(searchHorarioAgenteCriteria, false)) {
            return buildHorariosSinResultados("TODO_OCUPADO",
                    "Hay horarios cargados, pero ya no quedan libres (están ocupados o venció su plazo de reserva).");
        }

        return buildHorariosSinResultados("NO_ATIENDE_ESE_DIA",
                "No hay horarios cargados para esos filtros: no atiende ese día o en esa franja horaria.");

    }

    /**
     * Convierte un horario de la proyección a la respuesta del canal, con la fecha y hora legibles.
     *
     * @param horarioAgenteProyeccion {@code HorarioAgenteProyeccion} horario leído de la base
     * @return {@code ListHorarioDisponibleAgenteResponse} horario con su {@code slotId}, fecha-hora y franja
     */
    private ListHorarioDisponibleAgenteResponse toHorarioResponse(HorarioAgenteProyeccion horarioAgenteProyeccion) {

        LocalDateTime fechaHora = horarioAgenteProyeccion.fecha().atTime(horarioAgenteProyeccion.horaDesde());
        ListHorarioDisponibleAgenteResponse listHorarioDisponibleAgenteResponse = new ListHorarioDisponibleAgenteResponse(
                horarioAgenteProyeccion.slotId(), fechaHora, FormatoMensaje.fechaHoraTexto(fechaHora),
                FranjaHorariaAgente.of(horarioAgenteProyeccion.horaDesde()));
        return listHorarioDisponibleAgenteResponse;

    }

    /**
     * Arma el día legible con el día de la semana ({@code jueves 12/11/2026}).
     *
     * @param fecha {@code LocalDate} fecha de calendario
     * @return {@code String} el día de la semana y la fecha
     */
    private String buildFechaTexto(LocalDate fecha) {

        String fechaTexto = FormatoMensaje.diaSemana(fecha) + " " + FormatoMensaje.fecha(fecha);
        return fechaTexto;

    }

    /**
     * Indica si un filtro de texto trae algo (no es {@code null} ni está en blanco).
     *
     * @param texto {@code String} filtro de texto, o {@code null}
     * @return {@code boolean} {@code true} si tiene contenido
     */
    private boolean hasTexto(String texto) {

        return texto != null && !texto.isBlank();

    }

    /**
     * Devuelve la mayor de dos horas, ignorando la que sea {@code null}.
     *
     * @param hora {@code LocalTime} una hora, o {@code null}
     * @param otraHora {@code LocalTime} la otra hora, o {@code null}
     * @return {@code LocalTime} la mayor, o {@code null} si las dos son {@code null}
     */
    private LocalTime maxHora(LocalTime hora, LocalTime otraHora) {

        if (hora == null || otraHora == null) {
            return hora != null ? hora : otraHora;
        }
        return hora.isAfter(otraHora) ? hora : otraHora;

    }

    /**
     * Devuelve la menor de dos horas, ignorando la que sea {@code null}.
     *
     * @param hora {@code LocalTime} una hora, o {@code null}
     * @param otraHora {@code LocalTime} la otra hora, o {@code null}
     * @return {@code LocalTime} la menor, o {@code null} si las dos son {@code null}
     */
    private LocalTime minHora(LocalTime hora, LocalTime otraHora) {

        if (hora == null || otraHora == null) {
            return hora != null ? hora : otraHora;
        }
        return hora.isBefore(otraHora) ? hora : otraHora;

    }

    /**
     * Lista los médicos activos que atienden una prestación en la fecha dada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fecha {@code LocalDate} fecha de vigencia de la asignación
     * @return {@code List<ListMedicoAgenteResponse>} médicos ordenados por apellido y nombre
     */
    private List<ListMedicoAgenteResponse> findMedicosQueAtienden(UUID prestacionId, LocalDate fecha) {

        List<Medico> medicosQueAtienden = medicoPrestacionDomainService.findAsignacionesVigentesByPrestacion(prestacionId, fecha)
                .stream()
                .map(MedicoPrestacion::getMedico)
                .filter(medico -> medico.getDeletedAt() == null)
                .distinct()
                .sorted(Comparator.comparing(Medico::getApellido).thenComparing(Medico::getNombre))
                .toList();

        List<ListMedicoAgenteResponse> listMedicoAgenteResponses = agenteMapper.toListMedicoResponses(medicosQueAtienden);
        return listMedicoAgenteResponses;

    }

    /**
     * Lista las prestaciones publicadas que un médico atiende en la fecha dada.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param fecha {@code LocalDate} fecha de vigencia de la asignación
     * @return {@code List<Prestacion>} prestaciones publicadas ordenadas por nombre
     */
    private List<Prestacion> findPrestacionesQueAtiende(UUID medicoId, LocalDate fecha) {

        List<Prestacion> prestacionesAsignadas = medicoPrestacionDomainService.findAsignacionesVigentesByMedico(medicoId, fecha)
                .stream()
                .map(MedicoPrestacion::getPrestacion)
                .distinct()
                .toList();

        List<Prestacion> prestacionesQueAtiende = filterPublicadas(prestacionesAsignadas).stream()
                .sorted(Comparator.comparing(Prestacion::getNombre))
                .toList();
        return prestacionesQueAtiende;

    }

    /**
     * Deja solo las prestaciones cuyo estado vigente es {@code PUBLICADA}, manteniendo el orden.
     *
     * @param prestaciones {@code List<Prestacion>} prestaciones en cualquier estado
     * @return {@code List<Prestacion>} las publicadas
     */
    private List<Prestacion> filterPublicadas(List<Prestacion> prestaciones) {

        if (prestaciones.isEmpty()) {
            return List.of();
        }

        Map<UUID, EstadoPrestacion> estadosVigentes = historicoEstadoPrestacionDomainService.getEstadosVigentes(
                prestaciones.stream().map(Prestacion::getId).toList());

        List<Prestacion> prestacionesPublicadas = prestaciones.stream()
                .filter(prestacion -> estadosVigentes.get(prestacion.getId()) == EstadoPrestacion.PUBLICADA)
                .toList();
        return prestacionesPublicadas;

    }

    /**
     * Indica si alguna indicación vigente de la prestación requiere validación (el turno arranca en
     * {@code ESPERA_VALIDACION}, mismo criterio que la reserva).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code boolean} {@code true} si alguna requiere validación
     */
    private boolean isValidacionRequerida(UUID prestacionId) {

        boolean validacionRequerida = indicacionPrestacionDomainService
                .findIndicacionesPrestacionVigentesByPrestacionId(prestacionId)
                .stream()
                .anyMatch(IndicacionPrestacion::getRequiereValidacion);
        return validacionRequerida;

    }

    /**
     * Elige entre candidatos parecidos: el único si hay uno solo, o el que coincide exactamente
     * (sin mayúsculas ni tildes) con lo que escribió el paciente.
     *
     * @param candidatos {@code List<T>} candidatos parecidos (no vacía)
     * @param nombreDe {@code Function<T, String>} cómo obtener el nombre de un candidato
     * @param nombreBuscado {@code String} lo que escribió el paciente
     * @param <T> tipo del candidato
     * @return {@code T} el candidato elegido, o {@code null} si la elección es ambigua
     */
    private <T> T findUnicaOExacta(List<T> candidatos, Function<T, String> nombreDe, String nombreBuscado) {

        if (candidatos.size() == 1) {
            return candidatos.getFirst();
        }

        String nombreBuscadoNormalizado = NormalizadorBusqueda.normalizarTexto(nombreBuscado);
        List<T> exactos = candidatos.stream()
                .filter(candidato -> NormalizadorBusqueda.normalizarTexto(nombreDe.apply(candidato)).equals(nombreBuscadoNormalizado))
                .toList();

        T candidatoElegido = exactos.size() == 1 ? exactos.getFirst() : null;
        return candidatoElegido;

    }

    //endregion

}
