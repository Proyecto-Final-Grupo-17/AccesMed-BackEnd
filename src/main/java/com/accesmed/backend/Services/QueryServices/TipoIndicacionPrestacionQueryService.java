package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion_;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Criteria.TipoIndicacionPrestacionCriteria;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.GetTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.ListTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Repositories.TipoIndicacionPrestacionRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.TipoIndicacionPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de lectura para la entidad {@code TipoIndicacionPrestacion}, incluido el
 * filtrado dinámico por {@link TipoIndicacionPrestacionCriteria} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). {@code createSpecification} excluye
 * siempre las bajas lógicas ({@code deletedAt IS NULL}), sin exponer ese campo como filtro.
 * Los métodos públicos devuelven records de response mapeados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TipoIndicacionPrestacionQueryService extends AbstractFiltroQueryService<TipoIndicacionPrestacion, TipoIndicacionPrestacionCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionRepository tipoIndicacionPrestacionRepository;
    private final TipoIndicacionPrestacionMapper tipoIndicacionPrestacionMapper;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<TipoIndicacionPrestacion> getRepository() {

        return tipoIndicacionPrestacionRepository;

    }

    /**
     * Busca el tipo de indicación activo que cumple el criteria de filtrado dinámico
     * proporcionado (típicamente un criteria armado con igualdad por {@code id}).
     * A diferencia de {@link #findTiposIndicacionPrestacion}, devuelve un único tipo
     * mapeado (no paginado).
     *
     * @param criteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetTipoIndicacionPrestacionResponse} el tipo encontrado, mapeado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         tipo activo cumple el criteria
     */
    public GetTipoIndicacionPrestacionResponse findTipoIndicacionPrestacionByCriteria(TipoIndicacionPrestacionCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando tipo de indicación de prestación por criteria: {}", criteria);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        //Buscar el tipo por el criteria proporcionado
        TipoIndicacionPrestacion tipoExistente = findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún tipo de indicación activo que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "TIPO_INDICACION_PRESTACION_NO_ENCONTRADO",
                            "No se encontró ningún tipo de indicación que coincida con la búsqueda.");
                });

        //Mapear y devolver la respuesta
        GetTipoIndicacionPrestacionResponse getTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionMapper.toGetResponse(tipoExistente,
                tieneAuditoria ? tipoIndicacionPrestacionMapper.toAuditoria(tipoExistente) : null);
        return getTipoIndicacionPrestacionResponse;

    }

    /**
     * Lista tipos de indicación activos según el criteria de filtrado dinámico proporcionado.
     *
     * @param criteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListTipoIndicacionPrestacionResponse>} página de tipos
     *         que cumplen el criteria, mapeados
     */
    public PageResponse<ListTipoIndicacionPrestacionResponse> findTiposIndicacionPrestacion(TipoIndicacionPrestacionCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Listado de tipos de indicación iniciado: criteria={}, page={}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        //Buscar tipos que cumplen el criteria, paginados
        Page<TipoIndicacionPrestacion> tiposPagina = findByCriteria(criteria, pageable);

        //Mapear y devolver response
        PageResponse<ListTipoIndicacionPrestacionResponse> pageResponse = PageResponse.from(tiposPagina,
                tipo -> tipoIndicacionPrestacionMapper.toListResponse(tipo,
                        tieneAuditoria ? tipoIndicacionPrestacionMapper.toAuditoria(tipo) : null));
        return pageResponse;

    }

    /**
     * Traduce un {@link TipoIndicacionPrestacionCriteria} a la {@link Specification}
     * equivalente, combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<TipoIndicacionPrestacion>} especificación equivalente al criteria
     */
    @Override
    protected Specification<TipoIndicacionPrestacion> createSpecification(TipoIndicacionPrestacionCriteria criteria) {

        log.debug("Armando specification de tipos de indicación de prestación: criteria={}", criteria);

        Specification<TipoIndicacionPrestacion> specification = Specification
                .<TipoIndicacionPrestacion>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(TipoIndicacionPrestacion_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), TipoIndicacionPrestacion_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), TipoIndicacionPrestacion_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), TipoIndicacionPrestacion_.nombre));
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
