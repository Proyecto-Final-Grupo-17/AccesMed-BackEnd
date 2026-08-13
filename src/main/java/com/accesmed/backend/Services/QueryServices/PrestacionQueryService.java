package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion_;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Records.Prestacion.Criteria.PrestacionCriteria;
import com.accesmed.backend.Repositories.PrestacionRepository;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.EstadoPrestacionFilter;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Prestacion}, incluido el filtrado dinámico
 * por {@link PrestacionCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * Prestacion se retira por estados, no por baja lógica, así que {@code createSpecification}
 * no agrega ningún filtro de "activa" implícito: el estado se filtra explícitamente
 * joineando al tramo vigente del histórico ({@code fechaHoraFin} vacío) si el criteria lo
 * pide (el contrato de query string {@code estadoActual.equals=...} no cambia).
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
            specification = specification.and(buildEstadoVigenteSpecification(criteria.getEstadoActual()));
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

    /**
     * Arma el fragmento de {@link Specification} que filtra por el estado vigente de la
     * prestación con una subconsulta correlacionada {@code EXISTS} sobre
     * {@code HistoricoEstadoPrestacion} (la relación es unidireccional: no hay
     * {@code join} desde {@code Prestacion} al histórico). La subconsulta correlaciona el
     * tramo con la prestación del root, exige {@code fechaHoraFin} vacío (el tramo vigente)
     * y aplica los operadores del filtro sobre {@code h.estado}.
     *
     * @param filter {@code EstadoPrestacionFilter} operadores a aplicar sobre el estado vigente
     * @return {@code Specification<Prestacion>} fragmento que filtra por el estado vigente vía {@code EXISTS}
     */
    private Specification<Prestacion> buildEstadoVigenteSpecification(EstadoPrestacionFilter filter) {

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<HistoricoEstadoPrestacion> historico = subquery.from(HistoricoEstadoPrestacion.class);
            subquery.select(historico.get(HistoricoEstadoPrestacion_.id));

            Path<EstadoPrestacion> estado = historico.get(HistoricoEstadoPrestacion_.estado);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(historico.get(HistoricoEstadoPrestacion_.prestacion), root));
            predicates.add(cb.isNull(historico.get(HistoricoEstadoPrestacion_.fechaHoraFin)));

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
