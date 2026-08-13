package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Criteria.IndicacionPrestacionCriteria;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.CreateIndicacionesPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.ScheduleBajaIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionesPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ScheduleBajaIndicacionPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Request.CreateIndicacionPrestacionAnidadaRequest;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TipoIndicacionPrestacionDomainService;
import com.accesmed.backend.Services.Mappers.IndicacionPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.IndicacionPrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

        //Validar que los tipos de indicación existan y estén activos, indexados por id
        Map<UUID, TipoIndicacionPrestacion> tiposIndicacionPorId = createIndicacionesPrestacionRequest.indicaciones().stream()
                .map(CreateIndicacionPrestacionAnidadaRequest::tipoIndicacionPrestacionId)
                .distinct()
                .collect(Collectors.toMap(id -> id, tipoIndicacionPrestacionDomainService::findTipoIndicacionPrestacionActivoById));

        //Mapear las indicaciones a entidades, resolviendo prestación y tipo de indicación
        List<IndicacionPrestacion> indicacionesNuevas = indicacionPrestacionMapper.toEntities(
                createIndicacionesPrestacionRequest.indicaciones(), prestacionExistente, tiposIndicacionPorId);

        //Guardar las indicaciones
        List<IndicacionPrestacion> indicacionesGuardadas = indicacionPrestacionDomainService
                .saveIndicacionesPrestacion(indicacionesNuevas);

        //Devolver response mapeado
        List<CreateIndicacionPrestacionResponse> indicacionesResponse = indicacionPrestacionMapper
                .toCreateResponses(indicacionesGuardadas);
        CreateIndicacionesPrestacionResponse createIndicacionesPrestacionResponse =
                new CreateIndicacionesPrestacionResponse(indicacionesResponse);
        return createIndicacionesPrestacionResponse;

    }

    /**
     * Actualiza una indicación de prestación existente.
     *
     * @param updateIndicacionPrestacionRequest {@code UpdateIndicacionPrestacionRequest} datos a actualizar,
     *        incluyendo el id de la indicación (ya validado contra la ruta en el Controller)
     * @return {@code UpdateIndicacionPrestacionResponse} la indicación actualizada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la indicación no existe
     */
    @Transactional
    public UpdateIndicacionPrestacionResponse updateIndicacionPrestacion(
            UpdateIndicacionPrestacionRequest updateIndicacionPrestacionRequest) {

        UUID id = updateIndicacionPrestacionRequest.id();

        log.info("Actualización de indicación de prestación iniciada: id={}", id);

        //Buscar la indicación
        IndicacionPrestacion indicacionExistente = indicacionPrestacionDomainService
                .findIndicacionPrestacionVigenteById(id);

        //Actualizar
        indicacionPrestacionMapper.updateIndicacionPrestacion(indicacionExistente, updateIndicacionPrestacionRequest);

        //Persistir
        IndicacionPrestacion indicacionActualizada = indicacionPrestacionDomainService
                .saveIndicacionPrestacion(indicacionExistente);

        //Devolver response mapeado
        UpdateIndicacionPrestacionResponse updateIndicacionPrestacionResponse = indicacionPrestacionMapper
                .toUpdateResponse(indicacionActualizada);
        return updateIndicacionPrestacionResponse;

    }

    /**
     * Programa la baja de una indicación de prestación, cerrando su vigencia. Admite una
     * fecha futura para dejar el retiro agendado.
     *
     * @param scheduleBajaIndicacionPrestacionRequest {@code ScheduleBajaIndicacionPrestacionRequest}
     *        fecha de fin de vigencia opcional, incluyendo el id de la indicación (ya validado
     *        contra la ruta en el Controller)
     * @return {@code ScheduleBajaIndicacionPrestacionResponse} la confirmación de la baja programada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la indicación no existe
     * @throws com.accesmed.backend.Services.Errors.ValidacionException
     *         {@code ValidacionException} si la fecha de fin de vigencia no es posterior al inicio
     */
    @Transactional
    public ScheduleBajaIndicacionPrestacionResponse scheduleBajaIndicacionPrestacion(
            ScheduleBajaIndicacionPrestacionRequest scheduleBajaIndicacionPrestacionRequest) {

        UUID id = scheduleBajaIndicacionPrestacionRequest.id();

        log.info("Baja de indicación de prestación iniciada: id={}", id);

        //Buscar la indicación vigente
        IndicacionPrestacion indicacionExistente = indicacionPrestacionDomainService
                .findIndicacionPrestacionVigenteById(id);

        //Si no vino fecha de fin de vigencia, la baja es inmediata
        ZonedDateTime fechaFinVigencia = scheduleBajaIndicacionPrestacionRequest.fechaFinVigencia() != null
                ? scheduleBajaIndicacionPrestacionRequest.fechaFinVigencia()
                : ZonedDateTime.now();

        //Cerrar la vigencia
        indicacionPrestacionDomainService.cerrarVigenciaIndicacionPrestacion(indicacionExistente, fechaFinVigencia);

        //Devolver response mapeado
        ScheduleBajaIndicacionPrestacionResponse scheduleBajaIndicacionPrestacionResponse = indicacionPrestacionMapper
                .toScheduleBajaResponse(indicacionExistente);
        return scheduleBajaIndicacionPrestacionResponse;

    }

    /**
     * Busca la indicación de prestación vigente que cumple el criteria de filtrado
     * dinámico proporcionado. A diferencia de {@link #findIndicacionesPrestacion}, devuelve
     * una única indicación (no paginada) — pensado para criterios que identifican una
     * indicación puntual (ej. {@code id.equals}).
     *
     * @param indicacionPrestacionCriteria {@code IndicacionPrestacionCriteria} filtros a aplicar
     * @return {@code GetIndicacionPrestacionResponse} la indicación encontrada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si ninguna indicación cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetIndicacionPrestacionResponse findIndicacionPrestacionByCriteria(
            IndicacionPrestacionCriteria indicacionPrestacionCriteria) {

        log.info("Búsqueda de indicación de prestación iniciada: criteria={}", indicacionPrestacionCriteria);

        //Buscar la indicación
        IndicacionPrestacion indicacionExistente = indicacionPrestacionQueryService
                .findIndicacionPrestacionByCriteria(indicacionPrestacionCriteria);

        //Devolver response mapeado
        GetIndicacionPrestacionResponse getIndicacionPrestacionResponse = indicacionPrestacionMapper
                .toGetResponse(indicacionExistente);
        return getIndicacionPrestacionResponse;

    }

    /**
     * Lista indicaciones de prestación vigentes según el criteria de filtrado dinámico
     * proporcionado.
     *
     * @param indicacionPrestacionCriteria {@code IndicacionPrestacionCriteria} filtros a aplicar,
     *        o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListIndicacionPrestacionResponse>} página de indicaciones que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListIndicacionPrestacionResponse> findIndicacionesPrestacion(
            IndicacionPrestacionCriteria indicacionPrestacionCriteria, Pageable pageable) {

        log.info("Listado de indicaciones de prestación iniciado: criteria={}, page={}", indicacionPrestacionCriteria, pageable);

        //Buscar indicaciones que cumplen el criteria, paginadas
        Page<IndicacionPrestacion> indicacionesPagina = indicacionPrestacionQueryService
                .findByCriteria(indicacionPrestacionCriteria, pageable);

        //Devolver response mapeado
        PageResponse<ListIndicacionPrestacionResponse> pageResponse = PageResponse.from(
                indicacionesPagina, indicacionPrestacionMapper::toListResponse);
        return pageResponse;

    }

    //endregion

}
