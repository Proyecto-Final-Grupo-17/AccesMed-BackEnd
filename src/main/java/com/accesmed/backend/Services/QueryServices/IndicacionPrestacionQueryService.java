package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.IndicacionPrestacion_;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion_;
import com.accesmed.backend.Records.IndicacionPrestacion.Criteria.IndicacionPrestacionCriteria;
import com.accesmed.backend.Repositories.IndicacionPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

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
public class IndicacionPrestacionQueryService extends AbstractFiltroQueryService<IndicacionPrestacion, IndicacionPrestacionCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionRepository indicacionPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<IndicacionPrestacion> getRepository() {

        return indicacionPrestacionRepository;

    }

    /**
     * Busca la indicación de prestación vigente que cumple el criteria proporcionado
     * (típicamente un criteria armado con igualdad por {@code id}). A diferencia de
     * {@link #findByCriteria}, devuelve una única indicación en vez de una página.
     *
     * @param criteria {@code IndicacionPrestacionCriteria} filtros a aplicar
     * @return {@code IndicacionPrestacion} la indicación vigente que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna
     *         indicación vigente cumple el criteria
     */
    public IndicacionPrestacion findIndicacionPrestacionByCriteria(IndicacionPrestacionCriteria criteria) {

        log.debug("Buscando indicación de prestación por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna indicación de prestación vigente que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "INDICACION_PRESTACION_NO_ENCONTRADA",
                            "No existe una indicación de prestación vigente que cumpla el criteria proporcionado.");
                });

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
