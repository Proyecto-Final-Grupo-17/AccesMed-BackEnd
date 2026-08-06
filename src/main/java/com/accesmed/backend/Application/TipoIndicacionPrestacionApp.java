package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Criteria.TipoIndicacionPrestacionCriteria;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.UpdateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.CreateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.UpdateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.CreateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.ListTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.GetTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.SoftDeleteTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TipoIndicacionPrestacionDomainService;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.TipoIndicacionPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.TipoIndicacionPrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso de Tipo de Indicación de Prestación. Orquesta el flujo completo de los
 * endpoints (creación, actualización, baja) validando reglas de negocio y coordinando
 * los services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TipoIndicacionPrestacionApp {

    //region ========== Dependencias ==============

    //Domain Services
    private final TipoIndicacionPrestacionDomainService tipoIndicacionPrestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;

    //Query Services
    private final TipoIndicacionPrestacionQueryService tipoIndicacionPrestacionQueryService;

    //Mappers
    private final TipoIndicacionPrestacionMapper tipoIndicacionPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un tipo de indicación de prestación nuevo.
     *
     * @param createTipoIndicacionPrestacionRequest {@code CreateTipoIndicacionPrestacionRequest} datos del tipo
     * @return {@code CreateTipoIndicacionPrestacionResponse} el tipo creado
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el código o nombre ya existen
     */
    @Transactional
    public CreateTipoIndicacionPrestacionResponse createTipoIndicacionPrestacion(
            CreateTipoIndicacionPrestacionRequest createTipoIndicacionPrestacionRequest) {

        log.info("Creación de tipo de indicación iniciada: código={}", createTipoIndicacionPrestacionRequest.codigo());

        //Validar que el código no esté repetido
        tipoIndicacionPrestacionDomainService.validateCodigoTipoIndicacionPrestacionIsUnique(
                createTipoIndicacionPrestacionRequest.codigo());

        //Validar que el nombre no esté repetido
        tipoIndicacionPrestacionDomainService.validateNombreTipoIndicacionPrestacionIsUnique(
                createTipoIndicacionPrestacionRequest.nombre());

        //Mapear a entidad
        TipoIndicacionPrestacion tipoNuevo = tipoIndicacionPrestacionMapper
                .toEntity(createTipoIndicacionPrestacionRequest);

        //Persistir
        TipoIndicacionPrestacion tipoGuardado = tipoIndicacionPrestacionDomainService
                .saveTipoIndicacionPrestacion(tipoNuevo);

        //Devolver response mapeado
        CreateTipoIndicacionPrestacionResponse createTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionMapper
                .toCreateResponse(tipoGuardado);
        return createTipoIndicacionPrestacionResponse;

    }

    /**
     * Actualiza un tipo de indicación de prestación existente.
     *
     * @param updateTipoIndicacionPrestacionRequest {@code UpdateTipoIndicacionPrestacionRequest} datos a actualizar,
     *        incluyendo el id del tipo (ya validado contra la ruta en el Controller)
     * @return {@code UpdateTipoIndicacionPrestacionResponse} el tipo actualizado
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el tipo no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el código o nombre ya existen en otro tipo
     */
    @Transactional
    public UpdateTipoIndicacionPrestacionResponse updateTipoIndicacionPrestacion(
            UpdateTipoIndicacionPrestacionRequest updateTipoIndicacionPrestacionRequest) {

        UUID id = updateTipoIndicacionPrestacionRequest.id();

        log.info("Actualización de tipo de indicación iniciada: id={}", id);

        //Buscar el tipo existente
        TipoIndicacionPrestacion tipoExistente = tipoIndicacionPrestacionDomainService
                .findTipoIndicacionPrestacionActivoById(id);

        //Validar que el código no esté repetido, excluyendo el id actual
        tipoIndicacionPrestacionDomainService.validateCodigoTipoIndicacionPrestacionIsUnique(
                updateTipoIndicacionPrestacionRequest.codigo(), id);

        //Validar que el nombre no esté repetido, excluyendo el id actual
        tipoIndicacionPrestacionDomainService.validateNombreTipoIndicacionPrestacionIsUnique(
                updateTipoIndicacionPrestacionRequest.nombre(), id);

        //Actualizar usando el mapper
        tipoIndicacionPrestacionMapper.updateTipoIndicacionPrestacion(tipoExistente,
                updateTipoIndicacionPrestacionRequest);

        //Persistir
        TipoIndicacionPrestacion tipoActualizado = tipoIndicacionPrestacionDomainService
                .saveTipoIndicacionPrestacion(tipoExistente);

        //Devolver response mapeado
        UpdateTipoIndicacionPrestacionResponse updateTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionMapper
                .toUpdateResponse(tipoActualizado);
        return updateTipoIndicacionPrestacionResponse;

    }

    /**
     * Da de baja un tipo de indicación de prestación (baja lógica y restrictiva).
     *
     * @param id {@code UUID} identificador del tipo
     * @return {@code SoftDeleteTipoIndicacionPrestacionResponse} la confirmación de la baja
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el tipo no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si hay indicaciones vigentes que lo referencian
     */
    @Transactional
    public SoftDeleteTipoIndicacionPrestacionResponse softDeleteTipoIndicacionPrestacion(UUID id) {

        log.info("Baja de tipo de indicación iniciada: id={}", id);

        //Buscar el tipo
        TipoIndicacionPrestacion tipoExistente = tipoIndicacionPrestacionDomainService
                .findTipoIndicacionPrestacionActivoById(id);

        //Validar que no esté en uso (esta es la ÚNICA validación restrictiva del sistema)
        if (indicacionPrestacionDomainService.existsIndicacionesVigentesByTipo(id)) {
            log.warn("No se puede dar de baja el tipo de indicación: hay indicaciones vigentes que lo referencian. id={}", id);
            throw new ReglaNegocioException(getClass(), "TIPO_INDICACION_PRESTACION_EN_USO",
                    "No se puede dar de baja el tipo de indicación porque hay indicaciones vigentes que lo referencian.");
        }

        //Dar de baja
        tipoIndicacionPrestacionDomainService.softDeleteTipoIndicacionPrestacion(tipoExistente, "Baja de tipo de indicación");

        //Devolver el response
        SoftDeleteTipoIndicacionPrestacionResponse softDeleteTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionMapper
                .toSoftDeleteResponse(tipoExistente);
        return softDeleteTipoIndicacionPrestacionResponse;

    }

    /**
     * Busca el tipo de indicación de prestación activo que cumple el criteria de filtrado
     * dinámico proporcionado. A diferencia de {@link #findTiposIndicacionPrestacion},
     * devuelve un único tipo (no paginado) — pensado para criterios que identifican un tipo
     * puntual (ej. {@code id.equals}).
     *
     * @param tipoIndicacionPrestacionCriteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar
     * @return {@code GetTipoIndicacionPrestacionResponse} el tipo encontrado
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si ningún tipo cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetTipoIndicacionPrestacionResponse findTipoIndicacionPrestacionByCriteria(
            TipoIndicacionPrestacionCriteria tipoIndicacionPrestacionCriteria) {

        log.info("Búsqueda de tipo de indicación iniciada: criteria={}", tipoIndicacionPrestacionCriteria);

        //Buscar el tipo
        TipoIndicacionPrestacion tipoExistente = tipoIndicacionPrestacionQueryService
                .findTipoIndicacionPrestacionByCriteria(tipoIndicacionPrestacionCriteria);

        //Devolver response mapeado
        GetTipoIndicacionPrestacionResponse getTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionMapper
                .toGetResponse(tipoExistente);

        return getTipoIndicacionPrestacionResponse;

    }

    /**
     * Lista tipos de indicación de prestación activos según el criteria de filtrado
     * dinámico proporcionado.
     *
     * @param tipoIndicacionPrestacionCriteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar,
     *        o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListTipoIndicacionPrestacionResponse>} página de tipos que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListTipoIndicacionPrestacionResponse> findTiposIndicacionPrestacion(
            TipoIndicacionPrestacionCriteria tipoIndicacionPrestacionCriteria, Pageable pageable) {

        log.info("Listado de tipos de indicación iniciado: criteria={}, page={}", tipoIndicacionPrestacionCriteria, pageable);

        //Buscar tipos de indicación que cumplen el criteria, paginados
        Page<TipoIndicacionPrestacion> tiposPagina = tipoIndicacionPrestacionQueryService
                .findByCriteria(tipoIndicacionPrestacionCriteria, pageable);

        //Devolver response mapeado
        PageResponse<ListTipoIndicacionPrestacionResponse> pageResponse = PageResponse.from(
                tiposPagina, tipoIndicacionPrestacionMapper::toListResponse);
        return pageResponse;

    }

    //endregion

}
