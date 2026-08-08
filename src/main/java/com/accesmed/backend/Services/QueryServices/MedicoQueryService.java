package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Records.Medico.Criteria.MedicoCriteria;
import com.accesmed.backend.Repositories.MedicoRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code Medico}, incluido el filtrado dinámico por
 * {@link MedicoCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoQueryService extends AbstractFiltroQueryService<Medico, MedicoCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoRepository medicoRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Medico> getRepository() {

        return medicoRepository;

    }

    /**
     * Busca el médico activo que cumple el criteria proporcionado (típicamente un
     * criteria armado con igualdad por {@code id}). A diferencia de {@link #findByCriteria},
     * devuelve un único médico en vez de una página.
     *
     * @param criteria {@code MedicoCriteria} filtros a aplicar
     * @return {@code Medico} el médico activo que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         médico activo cumple el criteria
     */
    public Medico findMedicoByCriteria(MedicoCriteria criteria) {

        log.debug("Buscando médico por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún médico activo que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_NO_ENCONTRADO",
                            "No existe un médico activo que cumpla el criteria proporcionado.");
                });

    }

    /**
     * Traduce un {@link MedicoCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code MedicoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Medico>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Medico> createSpecification(MedicoCriteria criteria) {

        log.debug("Armando specification de médicos: criteria={}", criteria);

        Specification<Medico> specification = Specification
                .where((Specification<Medico>) null)
                .and((root, query, cb) -> cb.isNull(root.get(Medico_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Medico_.id));
        }
        if (criteria.getMatricula() != null) {
            specification = specification.and(buildStringSpecification(criteria.getMatricula(), Medico_.matricula));
        }
        if (criteria.getDni() != null) {
            specification = specification.and(buildStringSpecification(criteria.getDni(), Medico_.dni));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Medico_.nombre));
        }
        if (criteria.getApellido() != null) {
            specification = specification.and(buildStringSpecification(criteria.getApellido(), Medico_.apellido));
        }
        if (criteria.getEmail() != null) {
            specification = specification.and(buildStringSpecification(criteria.getEmail(), Medico_.email));
        }
        if (criteria.getEspecialidadId() != null) {
            specification = specification.and(buildSpecification(criteria.getEspecialidadId(),
                    root -> root.join(Medico_.especialidad, JoinType.LEFT).get(Especialidad_.id)));
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
