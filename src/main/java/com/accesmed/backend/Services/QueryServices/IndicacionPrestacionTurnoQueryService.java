package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.IndicacionPrestacion_;
import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import com.accesmed.backend.Domain.IndicacionPrestacionTurno_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Turno_;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.IndicacionPrestacionTurno.Criteria.IndicacionPrestacionTurnoCriteria;
import com.accesmed.backend.Records.IndicacionPrestacionTurno.Response.GetIndicacionPrestacionTurnoResponse;
import com.accesmed.backend.Records.IndicacionPrestacionTurno.Response.ListIndicacionPrestacionTurnoResponse;
import com.accesmed.backend.Repositories.IndicacionPrestacionTurnoRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.IndicacionPrestacionTurnoMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de lectura para la entidad {@code IndicacionPrestacionTurno}, incluido el
 * filtrado dinámico por {@link IndicacionPrestacionTurnoCriteria} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). {@code IndicacionPrestacionTurno}
 * tiene baja lógica ({@code deletedAt}), así que {@code createSpecification} excluye
 * siempre las dadas de baja ({@code deletedAt IS NULL}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicacionPrestacionTurnoQueryService
        extends AbstractFiltroQueryService<IndicacionPrestacionTurno, IndicacionPrestacionTurnoCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionTurnoRepository indicacionPrestacionTurnoRepository;
    private final IndicacionPrestacionTurnoMapper indicacionPrestacionTurnoMapper;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<IndicacionPrestacionTurno> getRepository() {
        return indicacionPrestacionTurnoRepository;
    }

    /**
     * Busca la indicación de prestación de turno vigente que cumple el criteria de filtrado
     * dinámico proporcionado (típicamente un criteria armado con igualdad por {@code id}).
     * A diferencia de {@link #findIndicacionesPrestacionTurno}, devuelve una única indicación
     * mapeada (no paginada).
     *
     * @param criteria {@code IndicacionPrestacionTurnoCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetIndicacionPrestacionTurnoResponse} la indicación encontrada, mapeada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna
     *         indicación vigente cumple el criteria
     */
    public GetIndicacionPrestacionTurnoResponse findIndicacionPrestacionTurnoByCriteria(IndicacionPrestacionTurnoCriteria criteria,
            UsuarioDetails usuarioDetails) {

        log.debug("Buscando indicación de prestación de turno por criteria: {}", criteria);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        IndicacionPrestacionTurno indicacionExistente = findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna indicación de prestación de turno que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "INDICACION_PRESTACION_TURNO_NO_ENCONTRADA",
                            "No se encontró ninguna indicación del turno que coincida con la búsqueda.");
                });

        GetIndicacionPrestacionTurnoResponse getIndicacionPrestacionTurnoResponse = indicacionPrestacionTurnoMapper.toGetResponse(
                indicacionExistente, tieneAuditoria ? indicacionPrestacionTurnoMapper.toAuditoria(indicacionExistente) : null);
        return getIndicacionPrestacionTurnoResponse;

    }

    /**
     * Lista indicaciones de prestación de turno según el criteria de filtrado dinámico
     * proporcionado.
     *
     * @param criteria {@code IndicacionPrestacionTurnoCriteria} filtros a aplicar, o
     *         {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListIndicacionPrestacionTurnoResponse>} página de
     *         indicaciones que cumplen el criteria, mapeadas
     */
    public PageResponse<ListIndicacionPrestacionTurnoResponse> findIndicacionesPrestacionTurno(
            IndicacionPrestacionTurnoCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Listado de indicaciones de prestación de turno iniciado: criteria={}, page={}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        Page<IndicacionPrestacionTurno> indicacionesPagina = findByCriteria(criteria, pageable);

        PageResponse<ListIndicacionPrestacionTurnoResponse> pageResponse = PageResponse.from(indicacionesPagina,
                indicacionPrestacionTurno -> indicacionPrestacionTurnoMapper.toListResponse(indicacionPrestacionTurno,
                        tieneAuditoria ? indicacionPrestacionTurnoMapper.toAuditoria(indicacionPrestacionTurno) : null));
        return pageResponse;

    }

    /**
     * Traduce un {@link IndicacionPrestacionTurnoCriteria} a la {@link Specification}
     * equivalente, combinando un fragmento por cada campo filtrable que vino con valor.
     * Excluye siempre las indicaciones dadas de baja ({@code deletedAt IS NULL}).
     *
     * @param criteria {@code IndicacionPrestacionTurnoCriteria} filtros a aplicar, o
     *         {@code null} para no filtrar
     * @return {@code Specification<IndicacionPrestacionTurno>} especificación equivalente al
     *         criteria
     */
    @Override
    protected Specification<IndicacionPrestacionTurno> createSpecification(IndicacionPrestacionTurnoCriteria criteria) {

        log.debug("Armando specification de indicaciones de prestación de turno: criteria={}", criteria);

        Specification<IndicacionPrestacionTurno> specification = Specification
                .<IndicacionPrestacionTurno>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(IndicacionPrestacionTurno_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), IndicacionPrestacionTurno_.id));
        }
        if (criteria.getTurnoId() != null) {
            specification = specification.and(buildSpecification(criteria.getTurnoId(),
                    root -> root.join(IndicacionPrestacionTurno_.turno, JoinType.LEFT).get(Turno_.id)));
        }
        if (criteria.getIndicacionPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getIndicacionPrestacionId(),
                    root -> root.join(IndicacionPrestacionTurno_.indicacionPrestacion, JoinType.LEFT).get(IndicacionPrestacion_.id)));
        }
        if (criteria.getCreatedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getCreatedDate(), Auditable_.createdDate));
        }
        if (criteria.getLastModifiedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getLastModifiedDate(), Auditable_.lastModifiedDate));
        }
        if (criteria.getCreatedBy() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCreatedBy(), Auditable_.createdBy));
        }

        return specification;

    }

    //endregion

}
