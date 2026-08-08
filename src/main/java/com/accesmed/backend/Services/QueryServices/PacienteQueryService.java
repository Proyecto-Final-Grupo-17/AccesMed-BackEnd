package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Paciente_;
import com.accesmed.backend.Records.Paciente.Criteria.PacienteCriteria;
import com.accesmed.backend.Repositories.PacienteRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code Paciente}, incluido el filtrado dinámico
 * por {@link PacienteCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacienteQueryService extends AbstractFiltroQueryService<Paciente, PacienteCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final PacienteRepository pacienteRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Paciente> getRepository() {

        return pacienteRepository;

    }

    /**
     * Busca el paciente activo que cumple el criteria proporcionado (típicamente un
     * criteria armado con igualdad por {@code id}). A diferencia de {@link #findByCriteria},
     * devuelve un único paciente en vez de una página.
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar
     * @return {@code Paciente} el paciente activo que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         paciente activo cumple el criteria
     */
    public Paciente findPacienteByCriteria(PacienteCriteria criteria) {

        log.debug("Buscando paciente por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún paciente activo que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "PACIENTE_NO_ENCONTRADO",
                            "No existe un paciente activo que cumpla el criteria proporcionado.");
                });

    }

    /**
     * Traduce un {@link PacienteCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Paciente>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Paciente> createSpecification(PacienteCriteria criteria) {

        log.debug("Armando specification de pacientes: criteria={}", criteria);

        Specification<Paciente> specification = Specification
                .where((Specification<Paciente>) null)
                .and((root, query, cb) -> cb.isNull(root.get(Paciente_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Paciente_.id));
        }
        if (criteria.getDni() != null) {
            specification = specification.and(buildStringSpecification(criteria.getDni(), Paciente_.dni));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Paciente_.nombre));
        }
        if (criteria.getApellido() != null) {
            specification = specification.and(buildStringSpecification(criteria.getApellido(), Paciente_.apellido));
        }
        if (criteria.getEmail() != null) {
            specification = specification.and(buildStringSpecification(criteria.getEmail(), Paciente_.email));
        }
        if (criteria.getNumeroTelefono() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNumeroTelefono(), Paciente_.numeroTelefono));
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
