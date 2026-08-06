package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial_;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Plan_;
import com.accesmed.backend.Records.Plan.Criteria.PlanCriteria;
import com.accesmed.backend.Repositories.PlanRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Plan}, incluido el filtrado dinámico por
 * {@link PlanCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Plan se
 * retira por estados, no por baja lógica, así que {@code createSpecification} no agrega
 * ningún filtro de "activo" implícito.
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
     * Lista todos los planes de una obra social determinada. Usada por {@code ObraSocialApp}
     * para anidar los planes en sus responses de creación/actualización/obtención — no es
     * el listado público de {@code Plan} (ver {@link #findByCriteria}).
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<Plan>} lista de planes de esa obra social
     */
    public List<Plan> findPlanesByObraSocial(UUID obraSocialId) {

        log.debug("Listando planes de obra social: {}", obraSocialId);

        return planRepository.findAllByObraSocialId(obraSocialId);

    }

    /**
     * Lista los planes no deshabilitados de una obra social determinada. Usada por la baja
     * restrictiva/cascada de {@code ObraSocial}.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<Plan>} lista de planes no deshabilitados de esa obra social
     */
    public List<Plan> findPlanesNoDeshabilitadosByObraSocial(UUID obraSocialId) {

        log.debug("Listando planes no deshabilitados de obra social: {}", obraSocialId);

        return planRepository.findAllByObraSocialIdAndEstadoActualNot(obraSocialId, EstadoPlan.DESHABILITADO);

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

        Specification<Plan> specification = Specification.where((Specification<Plan>) null);

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
            specification = specification.and(buildSpecification(criteria.getEstadoActual(), Plan_.estadoActual));
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

    //endregion

}
