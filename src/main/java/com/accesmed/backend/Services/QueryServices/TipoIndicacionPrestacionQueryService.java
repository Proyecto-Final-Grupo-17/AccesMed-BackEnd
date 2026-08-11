package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion_;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Criteria.TipoIndicacionPrestacionCriteria;
import com.accesmed.backend.Repositories.TipoIndicacionPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code TipoIndicacionPrestacion}, incluido el
 * filtrado dinámico por {@link TipoIndicacionPrestacionCriteria} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). {@code createSpecification} excluye
 * siempre las bajas lógicas ({@code deletedAt IS NULL}), sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TipoIndicacionPrestacionQueryService extends AbstractFiltroQueryService<TipoIndicacionPrestacion, TipoIndicacionPrestacionCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionRepository tipoIndicacionPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<TipoIndicacionPrestacion> getRepository() {

        return tipoIndicacionPrestacionRepository;

    }

    /**
     * Busca el tipo de indicación activo que cumple el criteria proporcionado
     * (típicamente un criteria armado con igualdad por {@code id}). A diferencia de
     * {@link #findByCriteria}, devuelve un único tipo en vez de una página.
     *
     * @param criteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar
     * @return {@code TipoIndicacionPrestacion} el tipo activo que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         tipo activo cumple el criteria
     */
    public TipoIndicacionPrestacion findTipoIndicacionPrestacionByCriteria(TipoIndicacionPrestacionCriteria criteria) {

        log.debug("Buscando tipo de indicación de prestación por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún tipo de indicación activo que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "TIPO_INDICACION_PRESTACION_NO_ENCONTRADO",
                            "No existe un tipo de indicación de prestación activo que cumpla el criteria proporcionado.");
                });

    }

    /**
     * Traduce un {@link TipoIndicacionPrestacionCriteria} a la {@link Specification}
     * equivalente, combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<TipoIndicacionPrestacion>} especificación equivalente al criteria
     */
    @Override
    protected Specification<TipoIndicacionPrestacion> createSpecification(TipoIndicacionPrestacionCriteria criteria) {

        log.debug("Armando specification de tipos de indicación de prestación: criteria={}", criteria);

        Specification<TipoIndicacionPrestacion> specification = Specification
                .<TipoIndicacionPrestacion>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(TipoIndicacionPrestacion_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), TipoIndicacionPrestacion_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), TipoIndicacionPrestacion_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), TipoIndicacionPrestacion_.nombre));
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
