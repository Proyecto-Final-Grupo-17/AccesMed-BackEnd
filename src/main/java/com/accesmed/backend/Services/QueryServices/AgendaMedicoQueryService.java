package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.AgendaMedico_;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaMedicoCriteria;
import com.accesmed.backend.Repositories.AgendaMedicoRepository;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

/**
 * Consultas de lectura para la entidad {@code AgendaMedico}, incluido el filtrado dinámico
 * por {@link AgendaMedicoCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * Sin guarda fija de vigencia: {@code createSpecification} no agrega ningún filtro
 * implícito, igual que {@code PrestacionQueryService} — el selector del front tiene que
 * poder listar también los períodos vencidos y los programados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaMedicoQueryService extends AbstractFiltroQueryService<AgendaMedico, AgendaMedicoCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaMedicoRepository agendaMedicoRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<AgendaMedico> getRepository() {

        return agendaMedicoRepository;

    }

    /**
     * Traduce un {@link AgendaMedicoCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code AgendaMedicoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<AgendaMedico>} especificación equivalente al criteria
     */
    @Override
    protected Specification<AgendaMedico> createSpecification(AgendaMedicoCriteria criteria) {

        log.debug("Armando specification de agendas médicas: criteria={}", criteria);

        Specification<AgendaMedico> specification = Specification.unrestricted();

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), AgendaMedico_.id));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    root -> root.join(AgendaMedico_.medico, JoinType.LEFT).get(Medico_.id)));
        }
        if (criteria.getEspecialidadId() != null) {
            specification = specification.and(buildSpecification(criteria.getEspecialidadId(),
                    root -> root.join(AgendaMedico_.medico, JoinType.LEFT).join(Medico_.especialidad, JoinType.LEFT).get(Especialidad_.id)));
        }
        if (criteria.getFechaHoraInicioVigencia() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFechaHoraInicioVigencia(), AgendaMedico_.fechaHoraInicioVigencia));
        }
        if (criteria.getFechaHoraFinVigencia() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFechaHoraFinVigencia(), AgendaMedico_.fechaHoraFinVigencia));
        }
        if (criteria.getVigenteAl() != null) {
            specification = specification.and((root, query, cb) -> cb.and(
                    cb.lessThanOrEqualTo(root.get(AgendaMedico_.fechaHoraInicioVigencia), criteria.getVigenteAl()),
                    cb.greaterThan(root.get(AgendaMedico_.fechaHoraFinVigencia), criteria.getVigenteAl())));
        }

        return specification;

    }

    //endregion

}
