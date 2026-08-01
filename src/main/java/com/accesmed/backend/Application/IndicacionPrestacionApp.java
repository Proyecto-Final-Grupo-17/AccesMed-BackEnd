package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.CreateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionPrestacionResponse;
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
     * Crea una indicación de prestación nueva.
     *
     * @param createIndicacionPrestacionRequest {@code CreateIndicacionPrestacionRequest} datos de la indicación
     * @return {@code CreateIndicacionPrestacionResponse} la indicación creada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la prestación o tipo de indicación no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si la prestación está habilitada
     */
    @Transactional
    public CreateIndicacionPrestacionResponse createIndicacionPrestacion(
            CreateIndicacionPrestacionRequest createIndicacionPrestacionRequest) {

        log.info("Creación de indicación de prestación iniciada: nombre={}", createIndicacionPrestacionRequest.nombre());

        //Buscar la prestación y tipo de indicación
        var prestacionExistente = prestacionDomainService.findActivePrestacionById(createIndicacionPrestacionRequest.prestacionId());
        var tipoIndicacionExistente = tipoIndicacionPrestacionDomainService
                .findTipoIndicacionPrestacionById(createIndicacionPrestacionRequest.tipoIndicacionPrestacionId());

        //Mapear a entidad
        IndicacionPrestacion indicacionNueva = indicacionPrestacionMapper.toEntity(createIndicacionPrestacionRequest);
        indicacionNueva.setPrestacion(prestacionExistente);
        indicacionNueva.setTipoIndicacionPrestacion(tipoIndicacionExistente);

        //Persistir
        IndicacionPrestacion indicacionGuardada = indicacionPrestacionDomainService
                .saveIndicacionPrestacion(indicacionNueva);

        //Devolver response mapeado
        return indicacionPrestacionMapper.toCreateResponse(indicacionGuardada);

    }

    /**
     * Actualiza una indicación de prestación existente.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updateIndicacionPrestacionRequest {@code UpdateIndicacionPrestacionRequest} datos a actualizar
     * @return {@code UpdateIndicacionPrestacionResponse} la indicación actualizada
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si la indicación no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si la prestación está habilitada
     */
    @Transactional
    public UpdateIndicacionPrestacionResponse updateIndicacionPrestacion(
            UUID id, UpdateIndicacionPrestacionRequest updateIndicacionPrestacionRequest) {

        log.info("Actualización de indicación de prestación iniciada: id={}", id);

        //Buscar la indicación
        IndicacionPrestacion indicacionExistente = indicacionPrestacionDomainService
                .findIndicacionPrestacionById(id);

        //Validar que la prestación esté en borrador (no habilitada)
        prestacionDomainService.validatePrestacionAdmiteEdicionDeIndicaciones(indicacionExistente.getPrestacion());

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
