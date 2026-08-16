package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.AgendaDia_;
import com.accesmed.backend.Domain.AgendaHorarios;
import com.accesmed.backend.Domain.AgendaHorarios_;
import com.accesmed.backend.Domain.AgendaMedico_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Repositories.AgendaHorariosRepository;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Consultas de lectura para la entidad {@code AgendaHorarios}, incluido el filtrado
 * dinámico por {@link AgendaHorariosCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado
 * dinámico}). Lo comparten {@code listHorariosAgenda} (panel) y {@code listHorariosDisponibles}
 * (chatbot): {@code createSpecification} aplica la única guarda común a ambos
 * ({@code deletedAt} vacío); {@link #findHorariosDisponibles} agrega las guardas propias
 * del listado del chatbot, que no son filtros que el front pueda pedir o no pedir, sino
 * reglas fijas del endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaHorariosQueryService extends AbstractFiltroQueryService<AgendaHorarios, AgendaHorariosCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaHorariosRepository agendaHorariosRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<AgendaHorarios> getRepository() {

        return agendaHorariosRepository;

    }

    /**
     * Traduce un {@link AgendaHorariosCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor. Guarda fija
     * común a ambos listados: {@code deletedAt} vacío.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<AgendaHorarios>} especificación equivalente al criteria
     */
    @Override
    protected Specification<AgendaHorarios> createSpecification(AgendaHorariosCriteria criteria) {

        log.debug("Armando specification de horarios de agenda: criteria={}", criteria);

        Specification<AgendaHorarios> specification = Specification
                .<AgendaHorarios>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(AgendaHorarios_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), AgendaHorarios_.id));
        }
        if (criteria.getPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getPrestacionId(),
                    root -> root.join(AgendaHorarios_.prestacion, JoinType.LEFT).get(Prestacion_.id)));
        }
        if (criteria.getAgendaMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getAgendaMedicoId(),
                    root -> root.join(AgendaHorarios_.agendaDia, JoinType.LEFT).join(AgendaDia_.agendaMedico, JoinType.LEFT).get(AgendaMedico_.id)));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    root -> root.join(AgendaHorarios_.agendaDia, JoinType.LEFT).join(AgendaDia_.agendaMedico, JoinType.LEFT)
                            .join(AgendaMedico_.medico, JoinType.LEFT).get(Medico_.id)));
        }
        if (criteria.getFecha() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFecha(),
                    root -> root.join(AgendaHorarios_.agendaDia, JoinType.LEFT).get(AgendaDia_.fecha)));
        }
        if (criteria.getHoraDesde() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getHoraDesde(), AgendaHorarios_.horaDesde));
        }
        if (criteria.getEstaOcupada() != null) {
            specification = specification.and(buildSpecification(criteria.getEstaOcupada(), AgendaHorarios_.estaOcupada));
        }

        return specification;

    }

    /**
     * Busca horarios disponibles para el chatbot: sobre el criteria del cliente, aplica
     * además las guardas fijas propias de este listado ({@code estaOcupada = false},
     * {@code ahora < fechaLimiteReserva}, {@code fecha <= hoy + diasMaximosAnticipacionReserva}).
     * No son filtros opcionales: el front no puede pedir un slot ocupado ni uno fuera del
     * horizonte de reserva de la clínica.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @param diasMaximosAnticipacionReserva {@code int} horizonte de reserva configurado en la clínica
     * @return {@code Page<AgendaHorarios>} página de horarios disponibles que cumplen el criteria
     */
    public Page<AgendaHorarios> findHorariosDisponibles(AgendaHorariosCriteria criteria, Pageable pageable, int diasMaximosAnticipacionReserva) {

        ZonedDateTime ahora = ZonedDateTime.now();
        LocalDate fechaLimiteHorizonte = LocalDate.now().plusDays(diasMaximosAnticipacionReserva);

        Specification<AgendaHorarios> specification = createSpecification(criteria)
                .and((root, query, cb) -> cb.isFalse(root.get(AgendaHorarios_.estaOcupada)))
                .and((root, query, cb) -> cb.greaterThan(root.get(AgendaHorarios_.fechaLimiteReserva), ahora))
                .and((root, query, cb) -> cb.lessThanOrEqualTo(
                        root.join(AgendaHorarios_.agendaDia, JoinType.LEFT).get(AgendaDia_.fecha), fechaLimiteHorizonte));

        return agendaHorariosRepository.findAll(specification, pageable);

    }

    //endregion

}
