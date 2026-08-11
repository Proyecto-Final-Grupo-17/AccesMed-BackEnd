package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.ObraSocial_;
import com.accesmed.backend.Records.ObraSocial.Criteria.ObraSocialCriteria;
import com.accesmed.backend.Repositories.ObraSocialRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code ObraSocial}, incluido el filtrado dinámico
 * por {@link ObraSocialCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialQueryService extends AbstractFiltroQueryService<ObraSocial, ObraSocialCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialRepository obraSocialRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<ObraSocial> getRepository() {

        return obraSocialRepository;

    }

    /**
     * Busca la obra social activa que cumple el criteria proporcionado (típicamente un
     * criteria armado con igualdad por {@code id}). A diferencia de {@link #findByCriteria},
     * devuelve una única obra social en vez de una página.
     *
     * @param criteria {@code ObraSocialCriteria} filtros a aplicar
     * @return {@code ObraSocial} la obra social activa que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna
     *         obra social activa cumple el criteria
     */
    public ObraSocial findObraSocialByCriteria(ObraSocialCriteria criteria) {

        log.debug("Buscando obra social por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna obra social activa que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "OBRA_SOCIAL_NO_ENCONTRADA",
                            "No existe una obra social activa que cumpla el criteria proporcionado.");
                });

    }

    /**
     * Traduce un {@link ObraSocialCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code ObraSocialCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<ObraSocial>} especificación equivalente al criteria
     */
    @Override
    protected Specification<ObraSocial> createSpecification(ObraSocialCriteria criteria) {

        log.debug("Armando specification de obras sociales: criteria={}", criteria);

        Specification<ObraSocial> specification = Specification
                .<ObraSocial>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(ObraSocial_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), ObraSocial_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), ObraSocial_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), ObraSocial_.nombre));
        }
        if (criteria.getRazonSocial() != null) {
            specification = specification.and(buildStringSpecification(criteria.getRazonSocial(), ObraSocial_.razonSocial));
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
