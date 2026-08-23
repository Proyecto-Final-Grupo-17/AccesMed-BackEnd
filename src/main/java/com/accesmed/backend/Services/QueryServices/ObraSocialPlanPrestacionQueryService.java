package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.ObraSocial_;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion_;
import com.accesmed.backend.Domain.Plan_;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Records.ObraSocialPrestacion.Criteria.ObraSocialPrestacionCriteria;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.ListObraSocialPrestacionResponse;
import com.accesmed.backend.Repositories.ObraSocialPlanPrestacionRepository;
import com.accesmed.backend.Services.Mappers.ObraSocialPlanPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de lectura para la entidad {@code ObraSocialPlanPrestacion}, incluido el
 * filtrado dinámico por {@link ObraSocialPrestacionCriteria} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). {@code createSpecification} excluye
 * siempre las bajas lógicas ({@code deletedAt IS NULL}), sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ObraSocialPlanPrestacionQueryService
        extends AbstractFiltroQueryService<ObraSocialPlanPrestacion, ObraSocialPrestacionCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPlanPrestacionRepository obraSocialPlanPrestacionRepository;
    private final ObraSocialPlanPrestacionMapper obraSocialPlanPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<ObraSocialPlanPrestacion> getRepository() {

        return obraSocialPlanPrestacionRepository;

    }

    /**
     * Lista coberturas plan-prestación según el criteria de filtrado dinámico
     * proporcionado (ej. {@code planId.equals=} para las prestaciones de un plan puntual).
     *
     * @param criteria {@code ObraSocialPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} paginación (ordenamiento y límite)
     * @return {@code PageResponse<ListObraSocialPrestacionResponse>} página de DTOs mapeados
     */
    public PageResponse<ListObraSocialPrestacionResponse> findObraSocialPrestaciones(
            ObraSocialPrestacionCriteria criteria, Pageable pageable) {

        log.debug("Buscando coberturas plan-prestación por criteria: {}, pageable: {}", criteria, pageable);

        Page<ObraSocialPlanPrestacion> coberturasPaginadas = findByCriteria(criteria, pageable);

        PageResponse<ListObraSocialPrestacionResponse> pageResponse = PageResponse.from(
                coberturasPaginadas, obraSocialPlanPrestacionMapper::toListResponse);
        return pageResponse;

    }

    /**
     * Traduce un {@link ObraSocialPrestacionCriteria} a la {@link Specification}
     * equivalente, combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code ObraSocialPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<ObraSocialPlanPrestacion>} especificación equivalente al criteria
     */
    @Override
    protected Specification<ObraSocialPlanPrestacion> createSpecification(ObraSocialPrestacionCriteria criteria) {

        log.debug("Armando specification de coberturas plan-prestación: criteria={}", criteria);

        Specification<ObraSocialPlanPrestacion> specification = Specification
                .<ObraSocialPlanPrestacion>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(ObraSocialPlanPrestacion_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), ObraSocialPlanPrestacion_.id));
        }
        if (criteria.getPlanId() != null) {
            specification = specification.and(buildSpecification(criteria.getPlanId(),
                    root -> root.join(ObraSocialPlanPrestacion_.plan, JoinType.LEFT).get(Plan_.id)));
        }
        if (criteria.getPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getPrestacionId(),
                    root -> root.join(ObraSocialPlanPrestacion_.prestacion, JoinType.LEFT).get(Prestacion_.id)));
        }
        if (criteria.getObraSocialId() != null) {
            specification = specification.and(buildSpecification(criteria.getObraSocialId(),
                    root -> root.join(ObraSocialPlanPrestacion_.plan, JoinType.LEFT)
                            .join(Plan_.obraSocial, JoinType.LEFT)
                            .get(ObraSocial_.id)));
        }
        if (criteria.getModalidadCobertura() != null) {
            specification = specification.and(buildSpecification(criteria.getModalidadCobertura(), ObraSocialPlanPrestacion_.modalidadCobertura));
        }

        return specification;

    }

    //endregion

}
