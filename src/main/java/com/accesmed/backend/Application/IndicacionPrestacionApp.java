package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.CreateIndicacionesPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionesPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.SoftDeleteIndicacionPrestacionResponse;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TipoIndicacionPrestacionDomainService;
import com.accesmed.backend.Services.Mappers.IndicacionPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.IndicacionPrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Indicación de Prestación. Orquesta el flujo completo de los endpoints
 * (creación, actualización, baja) validando reglas de negocio y coordinando los services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicacionPrestacionApp {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final TipoIndicacionPrestacionDomainService tipoIndicacionPrestacionDomainService;
    private final IndicacionPrestacionQueryService indicacionPrestacionQueryService;
    private final IndicacionPrestacionMapper indicacionPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea varias indicaciones de prestación juntas, en una sola operación, todas
     * asociadas a la misma prestación.
     *
     * @param createIndicacionesPrestacionRequest {@code CreateIndicacionesPrestacionRequest} prestación e indicaciones a crear
     * @return {@code CreateIndicacionesPrestacionResponse} las indicaciones creadas
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la prestación o algún tipo de indicación no existe
     */
    @Transactional
    public CreateIndicacionesPrestacionResponse createIndicacionesPrestacion(
            CreateIndicacionesPrestacionRequest createIndicacionesPrestacionRequest) {

        log.info("Creación de indicaciones de prestación iniciada: prestacionId={}",
                createIndicacionesPrestacionRequest.prestacionId());

        //Buscar la prestación
        Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(createIndicacionesPrestacionRequest.prestacionId());

        //Mapear las indicaciones a entidades
        List<IndicacionPrestacion> indicacionesNuevas = indicacionPrestacionMapper
                .toEntities(createIndicacionesPrestacionRequest.indicaciones());

        //Resolver las relaciones que el mapper no puede resolver (requieren búsqueda por id)
        for (int i = 0; i < indicacionesNuevas.size(); i++) {
            TipoIndicacionPrestacion tipoIndicacionExistente = tipoIndicacionPrestacionDomainService
                    .findTipoIndicacionPrestacionActivoById(
                            createIndicacionesPrestacionRequest.indicaciones().get(i).tipoIndicacionPrestacionId());

            indicacionesNuevas.get(i).setPrestacion(prestacionExistente);
            indicacionesNuevas.get(i).setTipoIndicacionPrestacion(tipoIndicacionExistente);
        }

        //Guardar las indicaciones
        List<IndicacionPrestacion> indicacionesGuardadas = indicacionPrestacionDomainService
                .saveIndicacionesPrestacion(indicacionesNuevas);

        //Mapear a create Indicaciones Prestacion Response
        List<CreateIndicacionPrestacionResponse> indicacionesResponse = indicacionPrestacionMapper
                .toCreateResponses(indicacionesGuardadas);
        CreateIndicacionesPrestacionResponse createIndicacionesPrestacionResponse =
                new CreateIndicacionesPrestacionResponse(indicacionesResponse);

        //Retornar Respuesta
        return createIndicacionesPrestacionResponse;

    }

    /**
     * Actualiza una indicación de prestación existente.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updateIndicacionPrestacionRequest {@code UpdateIndicacionPrestacionRequest} datos a actualizar
     * @return {@code UpdateIndicacionPrestacionResponse} la indicación actualizada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la indicación no existe
     */
    @Transactional
    public UpdateIndicacionPrestacionResponse updateIndicacionPrestacion(
            UUID id, UpdateIndicacionPrestacionRequest updateIndicacionPrestacionRequest) {

        log.info("Actualización de indicación de prestación iniciada: id={}", id);

        //Buscar la indicación
        IndicacionPrestacion indicacionExistente = indicacionPrestacionDomainService
                .findIndicacionPrestacionById(id);

        //Actualizar
        indicacionPrestacionMapper.updateIndicacionPrestacion(indicacionExistente, updateIndicacionPrestacionRequest);

        //Persistir
        IndicacionPrestacion indicacionActualizada = indicacionPrestacionDomainService
                .saveIndicacionPrestacion(indicacionExistente);

        //Devolver response mapeado
        return indicacionPrestacionMapper.toUpdateResponse(indicacionActualizada);

    }

    /**
     * Da de baja una indicación de prestación (baja lógica).
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code SoftDeleteIndicacionPrestacionResponse} la confirmación de la baja
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la indicación no existe
     */
    @Transactional
    public SoftDeleteIndicacionPrestacionResponse softDeleteIndicacionPrestacion(UUID id) {

        log.info("Baja de indicación de prestación iniciada: id={}", id);

        //Buscar la indicación
        IndicacionPrestacion indicacionExistente = indicacionPrestacionDomainService
                .findIndicacionPrestacionById(id);

        //Dar de baja
        indicacionPrestacionDomainService.softDeleteIndicacionPrestacion(indicacionExistente, "Baja de indicación");

        //Devolver el response
        return indicacionPrestacionMapper.toSoftDeleteResponse(indicacionExistente);

    }

    /**
     * Busca una indicación de prestación por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code GetIndicacionPrestacionResponse} la indicación encontrada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la indicación no existe
     */
    @Transactional(readOnly = true)
    public GetIndicacionPrestacionResponse findIndicacionPrestacionById(UUID id) {

        log.info("Búsqueda de indicación de prestación iniciada: id={}", id);

        //Buscar la indicación
        IndicacionPrestacion indicacionExistente = indicacionPrestacionDomainService
                .findIndicacionPrestacionById(id);

        //Devolver response mapeado
        return indicacionPrestacionMapper.toGetResponse(indicacionExistente);

    }

    /**
     * Lista indicaciones de prestación según los filtros proporcionados.
     *
     * @param prestacionId {@code UUID} opcional, para filtrar por prestación
     * @return {@code List<ListIndicacionPrestacionResponse>} lista de indicaciones que cumplen los filtros
     */
    @Transactional(readOnly = true)
    public List<ListIndicacionPrestacionResponse> findIndicacionesPrestacion(UUID prestacionId) {

        log.info("Listado de indicaciones de prestación iniciado: prestacionId={}", prestacionId);

        List<IndicacionPrestacion> indicaciones;

        //Aplicar filtro si viene
        if (prestacionId != null) {
            indicaciones = indicacionPrestacionQueryService.findIndicacionesPrestacionByPrestacion(prestacionId);
        } else {
            indicaciones = indicacionPrestacionQueryService.findAllIndicacionesPrestacion();
        }

        //Mapear a response
        return indicaciones.stream()
                .map(indicacionPrestacionMapper::toListResponse)
                .toList();

    }

    //endregion

}
