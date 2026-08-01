package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.UpdateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.CreateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.UpdateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.CreateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.ListTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.GetTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.SoftDeleteTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Services.DomainServices.TipoIndicacionPrestacionDomainService;
import com.accesmed.backend.Services.Mappers.TipoIndicacionPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.TipoIndicacionPrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionDomainService tipoIndicacionPrestacionDomainService;
    private final TipoIndicacionPrestacionQueryService tipoIndicacionPrestacionQueryService;
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
        return tipoIndicacionPrestacionMapper.toCreateResponse(tipoGuardado);

    }

    /**
     * Actualiza un tipo de indicación de prestación existente.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updateTipoIndicacionPrestacionRequest {@code UpdateTipoIndicacionPrestacionRequest} datos a actualizar
     * @return {@code UpdateTipoIndicacionPrestacionResponse} el tipo actualizado
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el tipo no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el código o nombre ya existen en otro tipo
     */
    @Transactional
    public UpdateTipoIndicacionPrestacionResponse updateTipoIndicacionPrestacion(
            UUID id, UpdateTipoIndicacionPrestacionRequest updateTipoIndicacionPrestacionRequest) {

        log.info("Actualización de tipo de indicación iniciada: id={}", id);

        //Buscar el tipo existente
        TipoIndicacionPrestacion tipoExistente = tipoIndicacionPrestacionDomainService
                .findTipoIndicacionPrestacionById(id);

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
        return tipoIndicacionPrestacionMapper.toUpdateResponse(tipoActualizado);

    }

    /**
     * Da de baja un tipo de indicación de prestación (baja lógica y restrictiva).
     *
     * @param id {@code UUID} identificador del tipo
     * @return {@code SoftDeleteTipoIndicacionPrestacionResponse} la confirmación de la baja
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el tipo no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si hay indicaciones activas que lo referencian
     */
    @Transactional
    public SoftDeleteTipoIndicacionPrestacionResponse softDeleteTipoIndicacionPrestacion(UUID id) {

        log.info("Baja de tipo de indicación iniciada: id={}", id);

        //Buscar el tipo
        TipoIndicacionPrestacion tipoExistente = tipoIndicacionPrestacionDomainService
                .findTipoIndicacionPrestacionById(id);

        //Validar que no esté en uso (esta es la ÚNICA validación restrictiva del sistema)
        tipoIndicacionPrestacionDomainService.validateTipoIndicacionPrestacionIsNotInUse(id);

        //Dar de baja
        tipoIndicacionPrestacionDomainService.softDeleteTipoIndicacionPrestacion(tipoExistente, "Baja de tipo de indicación");

        //Devolver el response
        return tipoIndicacionPrestacionMapper.toSoftDeleteResponse(tipoExistente);

    }

    /**
     * Busca un tipo de indicación de prestación por su identificador.
     *
     * @param id {@code UUID} identificador del tipo
     * @return {@code GetTipoIndicacionPrestacionResponse} el tipo encontrado
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el tipo no existe
     */
    @Transactional(readOnly = true)
    public GetTipoIndicacionPrestacionResponse findTipoIndicacionPrestacionById(UUID id) {

        log.info("Búsqueda de tipo de indicación iniciada: id={}", id);

        //Buscar el tipo
        TipoIndicacionPrestacion tipoExistente = tipoIndicacionPrestacionDomainService
                .findTipoIndicacionPrestacionById(id);

        //Devolver response mapeado
        return tipoIndicacionPrestacionMapper.toGetResponse(tipoExistente);

    }

    /**
     * Lista todos los tipos de indicación de prestación activos.
     *
     * @return {@code List<ListTipoIndicacionPrestacionResponse>} lista de tipos activos
     */
    @Transactional(readOnly = true)
    public List<ListTipoIndicacionPrestacionResponse> findAllTiposIndicacionPrestacion() {

        log.info("Listado de tipos de indicación iniciado");

        //Listar todos
        List<TipoIndicacionPrestacion> tipos = tipoIndicacionPrestacionQueryService
                .findAllTiposIndicacionPrestacion();

        //Mapear a response
        return tipoIndicacionPrestacionMapper.toListResponses(tipos);

    }

    //endregion

}
