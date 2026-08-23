package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan_;
import com.accesmed.backend.Domain.ObraSocial_;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Plan_;
import com.accesmed.backend.Records.Plan.Criteria.PlanCriteria;
import com.accesmed.backend.Repositories.PlanRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.EstadoPlanFilter;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class PlanQueryService extends AbstractFiltroQueryService<Plan, PlanCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final PlanRepository planRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Plan> getRepository() {

        return planRepository;

    }

    /**
     * Busca el plan que cumple el criteria proporcionado (típicamente un criteria armado
     * con igualdad por {@code id}). A diferencia de {@link #findByCriteria}, devuelve un
     * único plan en vez de una página.
     *
     * @param criteria {@code PlanCriteria} filtros a aplicar
     * @return {@code Plan} el plan que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         plan cumple el criteria
     */
    public Plan findPlanByCriteria(PlanCriteria criteria) {

        log.debug("Buscando plan por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún plan que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "PLAN_NO_ENCONTRADO",
                            "No existe un plan que cumpla el criteria proporcionado.");
                });

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

        return specification;

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
