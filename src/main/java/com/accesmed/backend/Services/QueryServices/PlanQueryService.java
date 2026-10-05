package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan_;
import com.accesmed.backend.Domain.ObraSocial_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Plan_;
import com.accesmed.backend.Records.Plan.Criteria.PlanCriteria;
import com.accesmed.backend.Records.Plan.Response.GetCoberturaAnidadaResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Repositories.HistoricoEstadoPlanRepository;
import com.accesmed.backend.Repositories.ObraSocialPlanPrestacionRepository;
import com.accesmed.backend.Repositories.PlanRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.ObraSocialPlanPrestacionMapper;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.EstadoPlanFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consultas de lectura para la entidad {@code Plan}, incluido el filtrado dinámico por
 * {@link PlanCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Plan se
 * retira por estados, no por baja lógica, así que {@code createSpecification} no agrega
 * ningún filtro de "activo" implícito: el estado se filtra explícitamente joineando al
 * tramo vigente del histórico ({@code fechaHoraFin} vacío) si el criteria lo pide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService extends AbstractFiltroQueryService<Plan, PlanCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;
    private final HistoricoEstadoPlanRepository historicoEstadoPlanRepository;
    private final ObraSocialPlanPrestacionRepository obraSocialPlanPrestacionRepository;
    private final ObraSocialPlanPrestacionMapper obraSocialPlanPrestacionMapper;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Plan> getRepository() {

        return planRepository;

    }

    /**
     * Busca el plan que cumple el criteria proporcionado, devolviendo su response mapeado
     * incluyendo su estado vigente. A diferencia de {@link #findPlanes}, devuelve un
     * único plan en vez de una página — pensado para criterios que identifican un plan
     * puntual (ej. {@code id.equals}).
     *
     * @param criteria {@code PlanCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetPlanResponse} el plan encontrado con su estado vigente
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         plan cumple el criteria
     */
    public GetPlanResponse findPlanByCriteria(PlanCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando plan por criteria: {}", criteria);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        Plan planEncontrado = findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún plan que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "PLAN_NO_ENCONTRADO",
                            "No se encontró ningún plan que coincida con la búsqueda.");
                });

        List<GetPlanResponse> planesMapeados = mapPlanesConEstado(List.of(planEncontrado), tieneAuditoria);
        return planesMapeados.get(0);

    }

    /**
     * Lista planes según el criteria de filtrado dinámico proporcionado, devolviendo una
     * página mapeada a responses con su estado vigente.
     *
     * @param criteria {@code PlanCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} paginación (ordenamiento y límite)
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListPlanResponse>} página de DTOs mapeados con su estado vigente
     */
    public PageResponse<ListPlanResponse> findPlanes(PlanCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando planes por criteria: {}, pageable: {}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        Page<Plan> planesPaginada = findByCriteria(criteria, pageable);

        Map<UUID, EstadoPlan> estadosVigentes = resolverEstadosVigentes(
                planesPaginada.getContent().stream().map(Plan::getId).toList());

        PageResponse<ListPlanResponse> pageResponse = PageResponse.from(planesPaginada,
                plan -> planMapper.toListResponse(plan, estadosVigentes.get(plan.getId()),
                        tieneAuditoria ? planMapper.toAuditoria(plan) : null));
        return pageResponse;

    }

    /**
     * Traduce un {@link PlanCriteria} a la {@link Specification} equivalente, combinando
     * un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code PlanCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Plan>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Plan> createSpecification(PlanCriteria criteria) {

        log.debug("Armando specification de planes: criteria={}", criteria);

        Specification<Plan> specification = Specification.unrestricted();

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Plan_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), Plan_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Plan_.nombre));
        }
        if (criteria.getEstadoActual() != null) {
            specification = specification.and(buildEstadoVigenteSpecification(criteria.getEstadoActual()));
        }
        if (criteria.getObraSocialId() != null) {
            specification = specification.and(buildSpecification(criteria.getObraSocialId(),
                    root -> root.join(Plan_.obraSocial, JoinType.LEFT).get(ObraSocial_.id)));
        }
        if (criteria.getCreatedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getCreatedDate(), Auditable_.createdDate));
        }
        if (criteria.getLastModifiedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getLastModifiedDate(), Auditable_.lastModifiedDate));
        }
        if (criteria.getCreatedBy() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCreatedBy(), Auditable_.createdBy));
        }

        return specification;

    }

    /**
     * Mapea una lista de planes a sus responses de lectura detallada, resolviendo el estado
     * vigente de todos en una única consulta (evita N+1) y alimentándolo a cada response.
     *
     * @param planes {@code List<Plan>} planes a mapear
     * @param tieneAuditoria {@code boolean} si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code List<GetPlanResponse>} responses detalladas con su estado vigente
     */
    private List<GetPlanResponse> mapPlanesConEstado(List<Plan> planes, boolean tieneAuditoria) {

        List<UUID> planIds = planes.stream().map(Plan::getId).toList();

        Map<UUID, EstadoPlan> estadosVigentes = resolverEstadosVigentes(planIds);

        Map<UUID, List<GetCoberturaAnidadaResponse>> coberturasPorPlan = obraSocialPlanPrestacionRepository
                .findByPlan_IdInAndDeletedAtIsNull(planIds).stream()
                .collect(Collectors.groupingBy(
                        cobertura -> cobertura.getPlan().getId(),
                        Collectors.mapping(obraSocialPlanPrestacionMapper::toGetCoberturaAnidadaResponse, Collectors.toList())));

        return planes.stream()
                .map(plan -> planMapper.toGetResponse(plan, estadosVigentes.get(plan.getId()),
                        coberturasPorPlan.getOrDefault(plan.getId(), List.of()),
                        tieneAuditoria ? planMapper.toAuditoria(plan) : null))
                .toList();

    }

    /**
     * Resuelve el estado vigente de un conjunto de planes con una única consulta batch,
     * armando un {@code Map<UUID, EstadoPlan>} indexado por plan ID.
     *
     * @param planIds {@code List<UUID>} identificadores de los planes
     * @return {@code Map<UUID, EstadoPlan>} mapa plan ID → estado vigente
     */
    private Map<UUID, EstadoPlan> resolverEstadosVigentes(List<UUID> planIds) {

        return planIds.isEmpty()
                ? Map.of()
                : historicoEstadoPlanRepository.findByPlanIdInAndFechaHoraFinIsNull(planIds).stream()
                        .collect(Collectors.toMap(historico -> historico.getPlan().getId(), HistoricoEstadoPlan::getEstado));

    }

    /**
     * Arma el fragmento de {@link Specification} que filtra por el estado vigente del plan
     * con una subconsulta correlacionada {@code EXISTS} sobre {@code HistoricoEstadoPlan}
     * (la relación es unidireccional: no hay {@code join} desde {@code Plan} al histórico).
     * La subconsulta correlaciona el tramo con el plan del root, exige {@code fechaHoraFin}
     * vacío (el tramo vigente) y aplica los operadores del filtro sobre {@code h.estado}.
     *
     * @param filter {@code EstadoPlanFilter} operadores a aplicar sobre el estado vigente
     * @return {@code Specification<Plan>} fragmento que filtra por el estado vigente vía {@code EXISTS}
     */
    private Specification<Plan> buildEstadoVigenteSpecification(EstadoPlanFilter filter) {

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<HistoricoEstadoPlan> historico = subquery.from(HistoricoEstadoPlan.class);
            subquery.select(historico.get(HistoricoEstadoPlan_.id));

            Path<EstadoPlan> estado = historico.get(HistoricoEstadoPlan_.estado);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(historico.get(HistoricoEstadoPlan_.plan), root));
            predicates.add(cb.isNull(historico.get(HistoricoEstadoPlan_.fechaHoraFin)));

            if (filter.getEquals() != null) {
                predicates.add(cb.equal(estado, filter.getEquals()));
            }
            if (filter.getNotEquals() != null) {
                predicates.add(cb.notEqual(estado, filter.getNotEquals()));
            }
            if (filter.getIn() != null) {
                predicates.add(estado.in(filter.getIn()));
            }
            if (filter.getNotIn() != null) {
                predicates.add(estado.in(filter.getNotIn()).not());
            }

            subquery.where(predicates.toArray(new Predicate[0]));

            //specified == false pide "sin tramo vigente que cumpla"; el resto, que exista
            return (filter.getSpecified() != null && !filter.getSpecified())
                    ? cb.not(cb.exists(subquery))
                    : cb.exists(subquery);
        };

    }

    //endregion

}
