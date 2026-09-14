package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.HistoricoEstadoTurno;
import com.accesmed.backend.Domain.HistoricoEstadoTurno_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Paciente_;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Domain.Turno_;
import com.accesmed.backend.Records.Turno.Criteria.TurnoCriteria;
import com.accesmed.backend.Records.Turno.Response.ListTurnoResponse;
import com.accesmed.backend.Repositories.HistoricoEstadoTurnoRepository;
import com.accesmed.backend.Repositories.TurnoRepository;
import com.accesmed.backend.Services.Mappers.TurnoMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.EstadoTurnoFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consultas de lectura para la entidad {@code Turno}, incluido el filtrado dinámico
 * por {@link TurnoCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * El estado vigente se resuelve desde el histórico mediante una subconsulta
 * correlacionada {@code EXISTS}, ya que la relación es unidireccional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TurnoQueryService extends AbstractFiltroQueryService<Turno, TurnoCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final TurnoRepository turnoRepository;
    private final TurnoMapper turnoMapper;
    private final HistoricoEstadoTurnoRepository historicoEstadoTurnoRepository;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Turno> getRepository() {

        return turnoRepository;

    }

    /**
     * Lista turnos según el criteria de filtrado dinámico proporcionado, devolviendo
     * una página mapeada a responses con su estado vigente.
     *
     * @param criteria {@code TurnoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} paginación (ordenamiento y límite)
     * @return {@code PageResponse<ListTurnoResponse>} página de DTOs mapeados con su estado vigente
     */
    public PageResponse<ListTurnoResponse> findTurnos(TurnoCriteria criteria, Pageable pageable) {

        log.debug("Buscando turnos por criteria: {}, pageable: {}", criteria, pageable);

        Page<Turno> turnosPaginada = findByCriteria(criteria, pageable);

        Map<UUID, EstadoTurno> estadosVigentes = resolverEstadosVigentes(
                turnosPaginada.getContent().stream().map(Turno::getId).toList());

        PageResponse<ListTurnoResponse> pageResponse = PageResponse.from(turnosPaginada,
                turno -> turnoMapper.toListResponse(turno, estadosVigentes.get(turno.getId())));
        return pageResponse;

    }

    /**
     * Traduce un {@link TurnoCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code TurnoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Turno>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Turno> createSpecification(TurnoCriteria criteria) {

        log.debug("Armando specification de turnos: criteria={}", criteria);

        Specification<Turno> specification = Specification.unrestricted();

        if (criteria == null) {
            return specification;
        }

        if (criteria.getPacienteId() != null) {
            specification = specification.and(buildSpecification(criteria.getPacienteId(),
                    root -> root.join(Turno_.paciente, JoinType.LEFT).get(Paciente_.id)));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    root -> root.join(Turno_.medico, JoinType.LEFT).get(Medico_.id)));
        }
        if (criteria.getPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getPrestacionId(),
                    root -> root.join(Turno_.prestacion, JoinType.LEFT).get(Prestacion_.id)));
        }
        if (criteria.getEstadoActual() != null) {
            specification = specification.and(buildEstadoVigenteSpecification(criteria.getEstadoActual()));
        }
        if (criteria.getFechaHoraInicio() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFechaHoraInicio(), Turno_.fechaHoraInicio));
        }
        if (criteria.getCreatedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getCreatedDate(), Auditable_.createdDate));
        }
        if (criteria.getLastModifiedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getLastModifiedDate(), Auditable_.lastModifiedDate));
        }

        return specification;

    }

    /**
     * Resuelve el estado vigente de un conjunto de turnos con una única consulta
     * batch, armando un {@code Map<UUID, EstadoTurno>} indexado por turno ID.
     *
     * @param turnoIds {@code List<UUID>} identificadores de los turnos
     * @return {@code Map<UUID, EstadoTurno>} mapa turno ID → estado vigente
     */
    private Map<UUID, EstadoTurno> resolverEstadosVigentes(List<UUID> turnoIds) {

        return turnoIds.isEmpty()
                ? Map.of()
                : historicoEstadoTurnoRepository.findByTurno_IdInAndFechaHoraFinIsNull(turnoIds).stream()
                        .collect(Collectors.toMap(historico -> historico.getTurno().getId(), HistoricoEstadoTurno::getEstado));

    }

    /**
     * Arma el fragmento de {@link Specification} que filtra por el estado vigente del
     * turno con una subconsulta correlacionada {@code EXISTS} sobre
     * {@code HistoricoEstadoTurno} (la relación es unidireccional: no hay
     * {@code join} desde {@code Turno} al histórico). La subconsulta correlaciona el
     * tramo con el turno del root, exige {@code fechaHoraFin} vacío (el tramo vigente)
     * y aplica los operadores del filtro sobre {@code h.estado}.
     *
     * @param filter {@code EstadoTurnoFilter} operadores a aplicar sobre el estado vigente
     * @return {@code Specification<Turno>} fragmento que filtra por el estado vigente vía {@code EXISTS}
     */
    private Specification<Turno> buildEstadoVigenteSpecification(EstadoTurnoFilter filter) {

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<HistoricoEstadoTurno> historico = subquery.from(HistoricoEstadoTurno.class);
            subquery.select(historico.get(HistoricoEstadoTurno_.id));

            Path<EstadoTurno> estado = historico.get(HistoricoEstadoTurno_.estado);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(historico.get(HistoricoEstadoTurno_.turno), root));
            predicates.add(cb.isNull(historico.get(HistoricoEstadoTurno_.fechaHoraFin)));

            if (filter.getEquals() != null) {
                predicates.add(cb.equal(estado, filter.getEquals()));
            }
            if (filter.getNotEquals() != null) {
                predicates.add(cb.notEqual(estado, filter.getNotEquals()));
            }
            if (filter.getIn() != null) {
                predicates.add(estado.in(filter.getIn()));
            }
            if (filter.getNotIn() != null) {
                predicates.add(estado.in(filter.getNotIn()).not());
            }

            subquery.where(predicates.toArray(new Predicate[0]));

            //specified == false pide "sin tramo vigente que cumpla"; el resto, que exista
            return (filter.getSpecified() != null && !filter.getSpecified())
                    ? cb.not(cb.exists(subquery))
                    : cb.exists(subquery);
        };

    }

    //endregion

}
