package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Records.Especialidad.Criteria.EspecialidadCriteria;
import com.accesmed.backend.Records.Especialidad.Response.GetEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.ListEspecialidadResponse;
import com.accesmed.backend.Repositories.EspecialidadRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.EspecialidadMapper;
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
 * Consultas de lectura para la entidad {@code Especialidad}, incluido el filtrado dinámico
 * por {@link EspecialidadCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro. Los métodos públicos devuelven records de response
 * mapeados por {@link EspecialidadMapper}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EspecialidadQueryService extends AbstractFiltroQueryService<Especialidad, EspecialidadCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadRepository especialidadRepository;
    private final EspecialidadMapper especialidadMapper;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Especialidad> getRepository() {

        return especialidadRepository;

    }

    /**
     * Busca la especialidad activa que cumple el criteria de filtrado dinámico
     * proporcionado (típicamente un criteria armado con igualdad por {@code id}).
     * A diferencia de {@link #findEspecialidades}, devuelve una única especialidad
     * mapeada (no paginada).
     *
     * @param criteria {@code EspecialidadCriteria} filtros a aplicar
     * @return {@code GetEspecialidadResponse} la especialidad encontrada, mapeada a response
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna
     *         especialidad activa cumple el criteria
     */
    public GetEspecialidadResponse findEspecialidadByCriteria(EspecialidadCriteria criteria) {

        log.debug("Buscando especialidad por criteria: {}", criteria);

        Especialidad especialidadExistente = findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna especialidad activa que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                            "No existe una especialidad activa que cumpla el criteria proporcionado.");
                });

        GetEspecialidadResponse getEspecialidadResponse = especialidadMapper.toGetResponse(especialidadExistente);
        return getEspecialidadResponse;

    }

    /**
     * Lista especialidades activas según el criteria de filtrado dinámico proporcionado.
     *
     * @param criteria {@code EspecialidadCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListEspecialidadResponse>} página de especialidades
     *         que cumplen el criteria, mapeadas a response
     */
    public PageResponse<ListEspecialidadResponse> findEspecialidades(EspecialidadCriteria criteria, Pageable pageable) {

        log.debug("Listado de especialidades iniciado: criteria={}, page={}", criteria, pageable);

        //Buscar especialidades que cumplen el criteria, paginadas
        Page<Especialidad> especialidadesPagina = findByCriteria(criteria, pageable);

        //Mapear y devolver response
        PageResponse<ListEspecialidadResponse> pageResponse = PageResponse.from(especialidadesPagina, especialidadMapper::toListResponse);
        return pageResponse;

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
