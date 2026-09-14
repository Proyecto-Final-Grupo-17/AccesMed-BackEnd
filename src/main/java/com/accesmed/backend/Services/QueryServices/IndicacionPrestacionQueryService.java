package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.IndicacionPrestacion_;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion_;
import com.accesmed.backend.Records.IndicacionPrestacion.Criteria.IndicacionPrestacionCriteria;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Repositories.IndicacionPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.IndicacionPrestacionMapper;
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

import java.time.ZonedDateTime;

/**
 * Consultas de lectura para la entidad {@code IndicacionPrestacion}, incluido el filtrado
 * dinámico por {@link IndicacionPrestacionCriteria} (ver {@code Docs/ARQUITECTURA.md §7
 * Filtrado dinámico}). {@code IndicacionPrestacion} no tiene baja lógica: se retira
 * cerrando {@code fechaFinVigencia}, así que {@code createSpecification} excluye siempre
 * las no vigentes al momento de la consulta ({@code fechaInicioVigencia <= ahora AND
 * (fechaFinVigencia IS NULL OR fechaFinVigencia > ahora)}), equivalente al {@code deletedAt
 * IS NULL} de las entidades con baja lógica, sin exponer la vigencia como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicacionPrestacionQueryService extends AbstractFiltroQueryService<IndicacionPrestacion, IndicacionPrestacionCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionRepository indicacionPrestacionRepository;
    private final IndicacionPrestacionMapper indicacionPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<IndicacionPrestacion> getRepository() {

        return indicacionPrestacionRepository;

    }

    /**
     * Busca la indicación de prestación vigente que cumple el criteria de filtrado dinámico
     * proporcionado (típicamente un criteria armado con igualdad por {@code id}).
     * A diferencia de {@link #findIndicacionesPrestacion}, devuelve una única indicación
     * mapeada (no paginada).
     *
     * @param criteria {@code IndicacionPrestacionCriteria} filtros a aplicar
     * @return {@code GetIndicacionPrestacionResponse} la indicación encontrada, mapeada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna
     *         indicación vigente cumple el criteria
     */
    public GetIndicacionPrestacionResponse findIndicacionPrestacionByCriteria(IndicacionPrestacionCriteria criteria) {

        log.debug("Buscando indicación de prestación por criteria: {}", criteria);

        IndicacionPrestacion indicacionExistente = findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna indicación de prestación vigente que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "INDICACION_PRESTACION_NO_ENCONTRADA",
                            "No existe una indicación de prestación vigente que cumpla el criteria proporcionado.");
                });

        GetIndicacionPrestacionResponse getIndicacionPrestacionResponse = indicacionPrestacionMapper.toGetResponse(indicacionExistente);
        return getIndicacionPrestacionResponse;

    }

    /**
     * Lista indicaciones de prestación vigentes según el criteria de filtrado dinámico proporcionado.
     *
     * @param criteria {@code IndicacionPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListIndicacionPrestacionResponse>} página de indicaciones
     *         que cumplen el criteria, mapeadas
     */
    public PageResponse<ListIndicacionPrestacionResponse> findIndicacionesPrestacion(IndicacionPrestacionCriteria criteria, Pageable pageable) {

        log.debug("Listado de indicaciones de prestación iniciado: criteria={}, page={}", criteria, pageable);

        Page<IndicacionPrestacion> indicacionesPagina = findByCriteria(criteria, pageable);

        PageResponse<ListIndicacionPrestacionResponse> pageResponse = PageResponse.from(indicacionesPagina, indicacionPrestacionMapper::toListResponse);
        return pageResponse;

    }

    /**
     * Traduce un {@link IndicacionPrestacionCriteria} a la {@link Specification}
     * equivalente, combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code IndicacionPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<IndicacionPrestacion>} especificación equivalente al criteria
     */
    @Override
    protected Specification<IndicacionPrestacion> createSpecification(IndicacionPrestacionCriteria criteria) {

        log.debug("Armando specification de indicaciones de prestación: criteria={}", criteria);

        ZonedDateTime ahora = ZonedDateTime.now();
        Specification<IndicacionPrestacion> specification = Specification
                .<IndicacionPrestacion>unrestricted()
                .and((root, query, cb) -> cb.and(
                        cb.lessThanOrEqualTo(root.get(IndicacionPrestacion_.fechaInicioVigencia), ahora),
                        cb.or(
                                cb.isNull(root.get(IndicacionPrestacion_.fechaFinVigencia)),
                                cb.greaterThan(root.get(IndicacionPrestacion_.fechaFinVigencia), ahora)
                        )
                ));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), IndicacionPrestacion_.id));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), IndicacionPrestacion_.nombre));
        }
        if (criteria.getRequiereValidacion() != null) {
            specification = specification.and(buildSpecification(criteria.getRequiereValidacion(), IndicacionPrestacion_.requiereValidacion));
        }
        if (criteria.getPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getPrestacionId(),
                    root -> root.join(IndicacionPrestacion_.prestacion, JoinType.LEFT).get(Prestacion_.id)));
        }
        if (criteria.getTipoIndicacionPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getTipoIndicacionPrestacionId(),
                    root -> root.join(IndicacionPrestacion_.tipoIndicacionPrestacion, JoinType.LEFT).get(TipoIndicacionPrestacion_.id)));
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
