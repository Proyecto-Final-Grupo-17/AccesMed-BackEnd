package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.AgendaHorariosDia_;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.AgendaMedico_;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion_;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.MedicoPrestacion_;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaHorarioResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.Agente.Criteria.DiaConAgendaProyeccion;
import com.accesmed.backend.Records.Agente.Criteria.HorarioAgenteProyeccion;
import com.accesmed.backend.Records.Agente.Criteria.RangoAtencionMedicoProyeccion;
import com.accesmed.backend.Records.Agente.Criteria.SearchHorarioAgenteCriteria;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Repositories.AgendaHorariosDiaRepository;
import com.accesmed.backend.Repositories.ClinicaRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AlcanceMedicoService;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.AgendaMedicoMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code AgendaHorariosDia}, incluido el filtrado
 * dinámico por {@link AgendaHorariosCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado
 * dinámico}). Lo comparten {@code listHorariosAgenda} (panel) y {@code listHorariosDisponibles}
 * (chatbot): {@code createSpecification} aplica la única guarda común a ambos
 * ({@code deletedAt} vacío); {@link #findHorariosDisponibles} agrega las guardas propias
 * del listado del chatbot, que no son filtros que el front pueda pedir o no pedir, sino
 * reglas fijas del endpoint. Las consultas de la búsqueda flexible del canal chatbot
 * ({@code findFechasConHorariosDisponibles}, {@code findHorariosDisponiblesByCriteriaAgente}, etc.)
 * arman su propio predicado sobre joins internos y devuelven proyecciones, sin cargar entidades.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaHorariosDiaQueryService extends AbstractFiltroQueryService<AgendaHorariosDia, AgendaHorariosCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaHorariosDiaRepository agendaHorariosDiaRepository;
    private final AgendaMedicoMapper agendaMedicoMapper;
    private final ClinicaRepository clinicaRepository;
    private final AlcanceMedicoService alcanceMedicoService;
    private final AutorizacionService autorizacionService;
    private final EntityManager entityManager;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<AgendaHorariosDia> getRepository() {

        return agendaHorariosDiaRepository;

    }

    /**
     * Traduce un {@link AgendaHorariosCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor. Guarda fija
     * común a ambos listados: {@code deletedAt} vacío.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<AgendaHorariosDia>} especificación equivalente al criteria
     */
    @Override
    protected Specification<AgendaHorariosDia> createSpecification(AgendaHorariosCriteria criteria) {

        log.debug("Armando specification de horarios de agenda: criteria={}", criteria);

        Specification<AgendaHorariosDia> specification = Specification
                .<AgendaHorariosDia>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(AgendaHorariosDia_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), AgendaHorariosDia_.id));
        }
        if (criteria.getPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getPrestacionId(),
                    root -> root.join(AgendaHorariosDia_.prestacion, JoinType.LEFT).get(Prestacion_.id)));
        }
        if (criteria.getAgendaMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getAgendaMedicoId(),
                    root -> root.join(AgendaHorariosDia_.agendaMedico, JoinType.LEFT).get(AgendaMedico_.id)));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    root -> root.join(AgendaHorariosDia_.agendaMedico, JoinType.LEFT)
                            .join(AgendaMedico_.medico, JoinType.LEFT).get(Medico_.id)));
        }
        if (criteria.getFecha() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFecha(), AgendaHorariosDia_.fecha));
        }
        if (criteria.getHoraDesde() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getHoraDesde(), AgendaHorariosDia_.horaDesde));
        }
        if (criteria.getEstaOcupada() != null) {
            specification = specification.and(buildSpecification(criteria.getEstaOcupada(), AgendaHorariosDia_.estaOcupada));
        }
        if (criteria.getCreatedBy() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCreatedBy(), Auditable_.createdBy));
        }

        return specification;

    }

    /**
     * Busca horarios de agenda del panel según el criteria, devolviendo una página mapeada a DTOs.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListAgendaHorarioResponse>} página de horarios mapeados
     */
    public PageResponse<ListAgendaHorarioResponse> findHorariosByCriteria(AgendaHorariosCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando horarios de agenda por criteria: {}, pageable: {}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        // Aplicar scope: si el criterio trae medicoId, reemplazarlo con el del usuario si es médico
        AgendaHorariosCriteria criteriaEfectivo = criteria != null ? criteria : new AgendaHorariosCriteria();
        UUID medicoIdEfectivo = alcanceMedicoService.resolveMedicoId(usuarioDetails, criteriaEfectivo.getMedicoId() != null ? criteriaEfectivo.getMedicoId().getEquals() : null);
        if (medicoIdEfectivo != null) {
            criteriaEfectivo.setMedicoId(new com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter());
            criteriaEfectivo.getMedicoId().setEquals(medicoIdEfectivo);
        }

        Page<AgendaHorariosDia> horariosPaginados = findByCriteria(criteriaEfectivo, pageable);
        return PageResponse.from(horariosPaginados, horario -> agendaMedicoMapper.toListHorarioResponse(horario,
                tieneAuditoria ? agendaMedicoMapper.toAuditoria(horario) : null));

    }

    /**
     * Busca horarios disponibles para el chatbot: sobre el criteria del cliente, aplica
     * además las guardas fijas propias de este listado ({@code estaOcupada = false},
     * {@code ahora < fechaLimiteReserva}, {@code fecha <= hoy + diasMaximosAnticipacionReserva}).
     * No son filtros opcionales: el front no puede pedir un slot ocupado ni uno fuera del
     * horizonte de reserva de la clínica. El horizonte se obtiene de la configuración de la clínica.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListHorarioDisponibleResponse>} página de horarios disponibles mapeados
     */
    public PageResponse<ListHorarioDisponibleResponse> findHorariosDisponibles(AgendaHorariosCriteria criteria, Pageable pageable) {

        log.debug("Buscando horarios disponibles por criteria: {}, pageable: {}", criteria, pageable);

        //Resolver el horizonte de reserva configurado en la única fila de Clinica (garantizada por el esquema)
        int diasMaximosAnticipacionReserva = clinicaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No se encontró la fila de configuración de la clínica");
                    return new RecursoNoEncontradoException(getClass(), "CLINICA_NO_ENCONTRADA",
                            "Falta la configuración de la clínica. Contactá a soporte.");
                })
                .getDiasMaximosAnticipacionReserva();
        ZonedDateTime ahora = ZonedDateTime.now();
        LocalDate fechaLimiteHorizonte = LocalDate.now().plusDays(diasMaximosAnticipacionReserva);

        Specification<AgendaHorariosDia> specification = createSpecification(criteria)
                .and((root, query, cb) -> cb.isFalse(root.get(AgendaHorariosDia_.estaOcupada)))
                .and((root, query, cb) -> cb.greaterThan(root.get(AgendaHorariosDia_.fechaLimiteReserva), ahora))
                .and((root, query, cb) -> cb.lessThanOrEqualTo(root.get(AgendaHorariosDia_.fecha), fechaLimiteHorizonte));

        Page<AgendaHorariosDia> horariosPaginados = agendaHorariosDiaRepository.findAll(specification, pageable);
        return PageResponse.from(horariosPaginados, agendaMedicoMapper::toListHorarioDisponibleResponse);

    }

    //endregion

    //region ========== Métodos: búsqueda flexible del canal chatbot ==========

    /**
     * Indica si hay horarios que cumplan los criterios del canal chatbot. Con {@code soloDisponibles}
     * aplica las mismas guardas que la búsqueda (libre, dentro del plazo y del horizonte de reserva); sin
     * él solo descarta los borrados y los pasados, y sirve para distinguir "no atiende" de "está todo ocupado".
     *
     * @param criteria {@code SearchHorarioAgenteCriteria} filtros ya resueltos
     * @param soloDisponibles {@code boolean} {@code true} para exigir horarios libres y reservables
     * @return {@code boolean} {@code true} si existe al menos un horario
     */
    public boolean existsHorariosByCriteriaAgente(SearchHorarioAgenteCriteria criteria, boolean soloDisponibles) {

        Specification<AgendaHorariosDia> specification = (root, query, cb) -> {
            Join<AgendaMedico, Medico> medico = root.join(AgendaHorariosDia_.agendaMedico).join(AgendaMedico_.medico);
            Join<AgendaHorariosDia, Prestacion> prestacion = root.join(AgendaHorariosDia_.prestacion);
            return buildPredicadoAgente(criteria, soloDisponibles, cb, query, root, medico, prestacion);
        };

        boolean existenHorarios = agendaHorariosDiaRepository.count(specification) > 0;
        return existenHorarios;

    }

    /**
     * Obtiene los días, en orden y con tope, que tienen horarios disponibles según los criterios. Sirve
     * para paginar por día antes de traer los horarios (así la cantidad de filas que se leen está acotada).
     *
     * @param criteria {@code SearchHorarioAgenteCriteria} filtros ya resueltos
     * @param omitir {@code int} cantidad de días a saltear (páginas anteriores)
     * @param cantidad {@code int} cantidad máxima de días a devolver
     * @return {@code List<LocalDate>} días distintos con horarios disponibles, del más cercano al más lejano
     */
    public List<LocalDate> findFechasConHorariosDisponibles(SearchHorarioAgenteCriteria criteria, int omitir, int cantidad) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<LocalDate> query = cb.createQuery(LocalDate.class);
        Root<AgendaHorariosDia> root = query.from(AgendaHorariosDia.class);
        Join<AgendaMedico, Medico> medico = root.join(AgendaHorariosDia_.agendaMedico).join(AgendaMedico_.medico);
        Join<AgendaHorariosDia, Prestacion> prestacion = root.join(AgendaHorariosDia_.prestacion);

        query.select(root.get(AgendaHorariosDia_.fecha)).distinct(true)
                .where(buildPredicadoAgente(criteria, true, cb, query, root, medico, prestacion))
                .orderBy(cb.asc(root.get(AgendaHorariosDia_.fecha)));

        List<LocalDate> fechasConHorarios = entityManager.createQuery(query)
                .setFirstResult(omitir)
                .setMaxResults(cantidad)
                .getResultList();
        return fechasConHorarios;

    }

    /**
     * Obtiene los horarios disponibles que cumplen los criterios, con los datos del médico y la
     * prestación, en una sola consulta con joins internos (sin cargar entidades: no hay N+1). Ordena por
     * día, médico, prestación y hora.
     *
     * @param criteria {@code SearchHorarioAgenteCriteria} filtros ya resueltos
     * @return {@code List<HorarioAgenteProyeccion>} horarios disponibles ordenados
     */
    public List<HorarioAgenteProyeccion> findHorariosDisponiblesByCriteriaAgente(SearchHorarioAgenteCriteria criteria) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<AgendaHorariosDia> root = query.from(AgendaHorariosDia.class);
        Join<AgendaMedico, Medico> medico = root.join(AgendaHorariosDia_.agendaMedico).join(AgendaMedico_.medico);
        Join<AgendaHorariosDia, Prestacion> prestacion = root.join(AgendaHorariosDia_.prestacion);
        Join<Medico, Especialidad> especialidad = medico.join(Medico_.especialidad);

        query.multiselect(
                        root.get(AgendaHorariosDia_.id),
                        root.get(AgendaHorariosDia_.fecha),
                        root.get(AgendaHorariosDia_.horaDesde),
                        medico.get(Medico_.id),
                        medico.get(Medico_.nombre),
                        medico.get(Medico_.apellido),
                        medico.get(Medico_.matricula),
                        especialidad.get(Especialidad_.nombre),
                        prestacion.get(Prestacion_.id),
                        prestacion.get(Prestacion_.nombre))
                .where(buildPredicadoAgente(criteria, true, cb, query, root, medico, prestacion))
                .orderBy(cb.asc(root.get(AgendaHorariosDia_.fecha)), cb.asc(medico.get(Medico_.apellido)),
                        cb.asc(medico.get(Medico_.nombre)), cb.asc(prestacion.get(Prestacion_.nombre)),
                        cb.asc(root.get(AgendaHorariosDia_.horaDesde)));

        List<HorarioAgenteProyeccion> horariosDisponibles = entityManager.createQuery(query).getResultList().stream()
                .map(fila -> new HorarioAgenteProyeccion(fila.get(0, UUID.class), fila.get(1, LocalDate.class),
                        fila.get(2, LocalTime.class), fila.get(3, UUID.class), fila.get(4, String.class),
                        fila.get(5, String.class), fila.get(6, String.class), fila.get(7, String.class),
                        fila.get(8, UUID.class), fila.get(9, String.class)))
                .toList();
        return horariosDisponibles;

    }

    /**
     * Obtiene, por médico y día, el rango en el que atiende (primer inicio y último fin de sus horarios
     * cargados, libres u ocupados), en una sola consulta agrupada.
     *
     * @param medicoIds {@code Collection<UUID>} médicos de la respuesta
     * @param fechas {@code Collection<LocalDate>} días de la respuesta
     * @return {@code List<RangoAtencionMedicoProyeccion>} un rango por médico y día
     */
    public List<RangoAtencionMedicoProyeccion> findRangosAtencionMedicos(Collection<UUID> medicoIds, Collection<LocalDate> fechas) {

        if (medicoIds.isEmpty() || fechas.isEmpty()) {
            return List.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<AgendaHorariosDia> root = query.from(AgendaHorariosDia.class);
        Join<AgendaMedico, Medico> medico = root.join(AgendaHorariosDia_.agendaMedico).join(AgendaMedico_.medico);

        query.multiselect(
                        medico.get(Medico_.id),
                        root.get(AgendaHorariosDia_.fecha),
                        cb.least(root.<LocalTime>get(AgendaHorariosDia_.horaDesde)),
                        cb.greatest(root.<LocalTime>get(AgendaHorariosDia_.horaHasta)))
                .where(cb.isNull(root.get(AgendaHorariosDia_.deletedAt)),
                        medico.get(Medico_.id).in(medicoIds),
                        root.get(AgendaHorariosDia_.fecha).in(fechas))
                .groupBy(medico.get(Medico_.id), root.get(AgendaHorariosDia_.fecha));

        List<RangoAtencionMedicoProyeccion> rangosAtencion = entityManager.createQuery(query).getResultList().stream()
                .map(fila -> new RangoAtencionMedicoProyeccion(fila.get(0, UUID.class), fila.get(1, LocalDate.class),
                        fila.get(2, LocalTime.class), fila.get(3, LocalTime.class)))
                .toList();
        return rangosAtencion;

    }

    /**
     * Obtiene los días en que cada médico tiene agenda en un período, con cuántos horarios siguen libres
     * y dentro del plazo de reserva, en una sola consulta agrupada.
     *
     * @param medicoIds {@code Collection<UUID>} médicos
     * @param prestacionIds {@code Collection<UUID>} prestaciones a considerar, o {@code null} para todas
     * @param desde {@code LocalDate} primer día, inclusive
     * @param hasta {@code LocalDate} último día, inclusive
     * @return {@code List<DiaConAgendaProyeccion>} un registro por médico y día con agenda, del más cercano al más lejano
     */
    public List<DiaConAgendaProyeccion> findDiasConAgendaByMedicos(Collection<UUID> medicoIds,
            Collection<UUID> prestacionIds, LocalDate desde, LocalDate hasta) {

        if (medicoIds.isEmpty() || (prestacionIds != null && prestacionIds.isEmpty())) {
            return List.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<AgendaHorariosDia> root = query.from(AgendaHorariosDia.class);
        Join<AgendaMedico, Medico> medico = root.join(AgendaHorariosDia_.agendaMedico).join(AgendaMedico_.medico);

        ZonedDateTime ahora = ZonedDateTime.now();
        Expression<Long> horariosLibres = cb.sum(cb.<Long>selectCase()
                .when(cb.and(cb.isFalse(root.get(AgendaHorariosDia_.estaOcupada)),
                        cb.greaterThan(root.get(AgendaHorariosDia_.fechaLimiteReserva), ahora)), 1L)
                .otherwise(0L));

        List<Predicate> predicados = new ArrayList<>();
        predicados.add(cb.isNull(root.get(AgendaHorariosDia_.deletedAt)));
        predicados.add(medico.get(Medico_.id).in(medicoIds));
        predicados.add(cb.between(root.get(AgendaHorariosDia_.fecha), desde, hasta));
        if (prestacionIds != null) {
            predicados.add(root.get(AgendaHorariosDia_.prestacion).get(Prestacion_.id).in(prestacionIds));
        }

        query.multiselect(medico.get(Medico_.id), root.get(AgendaHorariosDia_.fecha), horariosLibres)
                .where(predicados.toArray(new Predicate[0]))
                .groupBy(medico.get(Medico_.id), root.get(AgendaHorariosDia_.fecha))
                .orderBy(cb.asc(root.get(AgendaHorariosDia_.fecha)));

        List<DiaConAgendaProyeccion> diasConAgenda = entityManager.createQuery(query).getResultList().stream()
                .map(fila -> new DiaConAgendaProyeccion(fila.get(0, UUID.class), fila.get(1, LocalDate.class),
                        fila.get(2, Long.class)))
                .toList();
        return diasConAgenda;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Arma el predicado de la búsqueda flexible del canal chatbot sobre joins internos
     * {@code agenda_horarios_dia ⋈ agenda_medico ⋈ medico ⋈ prestacion}: horario y médico no borrados,
     * filtros por médico, prestación, especialidad, día y hora de inicio, un {@code EXISTS} de la
     * asignación médico-prestación vigente en la fecha del horario y otro del estado vigente
     * {@code PUBLICADA} de la prestación (el estado vigente es el tramo del histórico con
     * {@code fecha_hora_fin} vacío). Con {@code soloDisponibles} agrega las reglas de
     * {@link #findHorariosDisponibles}: libre, dentro del plazo y del horizonte de reserva.
     *
     * @param criteria {@code SearchHorarioAgenteCriteria} filtros ya resueltos
     * @param soloDisponibles {@code boolean} {@code true} para exigir horarios libres y reservables
     * @param cb {@code CriteriaBuilder} constructor de criterios
     * @param query {@code CriteriaQuery<?>} consulta que se arma (para las subconsultas)
     * @param root {@code Root<AgendaHorariosDia>} raíz de la consulta
     * @param medico {@code Join<AgendaMedico, Medico>} join interno al médico
     * @param prestacion {@code Join<AgendaHorariosDia, Prestacion>} join interno a la prestación
     * @return {@code Predicate} la condición de la consulta
     */
    private Predicate buildPredicadoAgente(SearchHorarioAgenteCriteria criteria, boolean soloDisponibles,
            CriteriaBuilder cb, CriteriaQuery<?> query, Root<AgendaHorariosDia> root,
            Join<AgendaMedico, Medico> medico, Join<AgendaHorariosDia, Prestacion> prestacion) {

        List<Predicate> predicados = new ArrayList<>();
        predicados.add(cb.isNull(root.get(AgendaHorariosDia_.deletedAt)));
        predicados.add(cb.isNull(medico.get(Medico_.deletedAt)));

        //Filtros por identificadores (un conjunto vacío no encuentra nada)
        if (criteria.medicoIds() != null) {
            predicados.add(criteria.medicoIds().isEmpty() ? cb.disjunction() : medico.get(Medico_.id).in(criteria.medicoIds()));
        }
        if (criteria.prestacionIds() != null) {
            predicados.add(criteria.prestacionIds().isEmpty() ? cb.disjunction() : prestacion.get(Prestacion_.id).in(criteria.prestacionIds()));
        }
        if (criteria.especialidadIds() != null) {
            predicados.add(criteria.especialidadIds().isEmpty() ? cb.disjunction()
                    : prestacion.get(Prestacion_.especialidad).get(Especialidad_.id).in(criteria.especialidadIds()));
        }

        //Día y hora de inicio
        LocalDate hoy = LocalDate.now();
        predicados.add(cb.greaterThanOrEqualTo(root.get(AgendaHorariosDia_.fecha), hoy));
        if (criteria.fechaDesde() != null) {
            predicados.add(cb.greaterThanOrEqualTo(root.get(AgendaHorariosDia_.fecha), criteria.fechaDesde()));
        }
        if (criteria.fechaHasta() != null) {
            predicados.add(cb.lessThanOrEqualTo(root.get(AgendaHorariosDia_.fecha), criteria.fechaHasta()));
        }
        if (criteria.fechas() != null) {
            predicados.add(criteria.fechas().isEmpty() ? cb.disjunction() : root.get(AgendaHorariosDia_.fecha).in(criteria.fechas()));
        }
        if (criteria.horaDesdeMinima() != null) {
            predicados.add(cb.greaterThanOrEqualTo(root.get(AgendaHorariosDia_.horaDesde), criteria.horaDesdeMinima()));
        }
        if (criteria.horaDesdeMaxima() != null) {
            predicados.add(cb.lessThan(root.get(AgendaHorariosDia_.horaDesde), criteria.horaDesdeMaxima()));
        }

        //El médico tiene que seguir haciendo la prestación en la fecha del horario
        Subquery<UUID> asignacionVigente = query.subquery(UUID.class);
        Root<MedicoPrestacion> medicoPrestacion = asignacionVigente.from(MedicoPrestacion.class);
        asignacionVigente.select(medicoPrestacion.get(MedicoPrestacion_.id)).where(
                cb.equal(medicoPrestacion.get(MedicoPrestacion_.medico), medico),
                cb.equal(medicoPrestacion.get(MedicoPrestacion_.prestacion), prestacion),
                cb.lessThanOrEqualTo(medicoPrestacion.get(MedicoPrestacion_.fechaInicioVigencia), root.get(AgendaHorariosDia_.fecha)),
                cb.or(cb.isNull(medicoPrestacion.get(MedicoPrestacion_.fechaFinVigencia)),
                        cb.greaterThanOrEqualTo(medicoPrestacion.get(MedicoPrestacion_.fechaFinVigencia), root.get(AgendaHorariosDia_.fecha))));
        predicados.add(cb.exists(asignacionVigente));

        //La prestación tiene que estar publicada (estado vigente = tramo del histórico sin fecha de fin)
        Subquery<UUID> prestacionPublicada = query.subquery(UUID.class);
        Root<HistoricoEstadoPrestacion> historico = prestacionPublicada.from(HistoricoEstadoPrestacion.class);
        prestacionPublicada.select(historico.get(HistoricoEstadoPrestacion_.id)).where(
                cb.equal(historico.get(HistoricoEstadoPrestacion_.prestacion), prestacion),
                cb.isNull(historico.get(HistoricoEstadoPrestacion_.fechaHoraFin)),
                cb.equal(historico.get(HistoricoEstadoPrestacion_.estado), EstadoPrestacion.PUBLICADA));
        predicados.add(cb.exists(prestacionPublicada));

        //Reglas de reserva: libre, dentro del plazo y del horizonte
        if (soloDisponibles) {
            predicados.add(cb.isFalse(root.get(AgendaHorariosDia_.estaOcupada)));
            predicados.add(cb.greaterThan(root.get(AgendaHorariosDia_.fechaLimiteReserva), ZonedDateTime.now()));
            predicados.add(cb.lessThanOrEqualTo(root.get(AgendaHorariosDia_.fecha),
                    hoy.plusDays(findDiasMaximosAnticipacionReserva())));
        }

        Predicate predicadoAgente = cb.and(predicados.toArray(new Predicate[0]));
        return predicadoAgente;

    }

    /**
     * Obtiene el horizonte de reserva configurado en la única fila de {@code Clinica}.
     *
     * @return {@code int} días máximos de anticipación con los que se puede reservar
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si falta la configuración de la clínica
     */
    private int findDiasMaximosAnticipacionReserva() {

        int diasMaximosAnticipacionReserva = clinicaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No se encontró la fila de configuración de la clínica");
                    return new RecursoNoEncontradoException(getClass(), "CLINICA_NO_ENCONTRADA",
                            "Falta la configuración de la clínica. Contactá a soporte.");
                })
                .getDiasMaximosAnticipacionReserva();
        return diasMaximosAnticipacionReserva;

    }

    //endregion

}
