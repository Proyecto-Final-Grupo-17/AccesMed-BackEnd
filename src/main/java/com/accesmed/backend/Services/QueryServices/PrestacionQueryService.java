package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Records.Prestacion.Criteria.PrestacionCriteria;
import com.accesmed.backend.Repositories.PrestacionRepository;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code Prestacion}, incluido el filtrado dinámico
 * por {@link PrestacionCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * Prestacion se retira por estados, no por baja lógica, así que {@code createSpecification}
 * no agrega ningún filtro de "activa" implícito: el estado se filtra explícitamente por
 * {@code estadoActual} si el criteria lo pide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionQueryService extends AbstractFiltroQueryService<Prestacion, PrestacionCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionRepository prestacionRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Prestacion> getRepository() {

        return prestacionRepository;

    }

    /**
     * Traduce un {@link PrestacionCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code PrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Prestacion>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Prestacion> createSpecification(PrestacionCriteria criteria) {

        log.debug("Armando specification de prestaciones: criteria={}", criteria);

        Specification<Prestacion> specification = Specification.unrestricted();

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Prestacion_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), Prestacion_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Prestacion_.nombre));
        }
        if (criteria.getEstadoActual() != null) {
            specification = specification.and(buildSpecification(criteria.getEstadoActual(), Prestacion_.estadoActual));
        }
        if (criteria.getEspecialidadId() != null) {
            specification = specification.and(buildSpecification(criteria.getEspecialidadId(),
                    root -> root.join(Prestacion_.especialidad, JoinType.LEFT).get(Especialidad_.id)));
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
