package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Records.Especialidad.Criteria.EspecialidadCriteria;
import com.accesmed.backend.Repositories.EspecialidadRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code Especialidad}, incluido el filtrado dinámico
 * por {@link EspecialidadCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspecialidadQueryService extends AbstractFiltroQueryService<Especialidad, EspecialidadCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadRepository especialidadRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Especialidad> getRepository() {

        return especialidadRepository;

    }

    /**
     * Busca la especialidad activa que cumple el criteria proporcionado (típicamente un
     * criteria armado con igualdad por {@code id}). A diferencia de {@link #findByCriteria},
     * devuelve una única especialidad en vez de una página.
     *
     * @param criteria {@code EspecialidadCriteria} filtros a aplicar
     * @return {@code Especialidad} la especialidad activa que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna
     *         especialidad activa cumple el criteria
     */
    public Especialidad findEspecialidadByCriteria(EspecialidadCriteria criteria) {

        log.debug("Buscando especialidad por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna especialidad activa que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                            "No existe una especialidad activa que cumpla el criteria proporcionado.");
                });

    }

    /**
     * Traduce un {@link EspecialidadCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code EspecialidadCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Especialidad>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Especialidad> createSpecification(EspecialidadCriteria criteria) {

        log.debug("Armando specification de especialidades: criteria={}", criteria);

        Specification<Especialidad> specification = Specification
                .<Especialidad>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(Especialidad_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Especialidad_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), Especialidad_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Especialidad_.nombre));
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
