package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Request.CreateIndicacionPrestacionAnidadaRequest;
import com.accesmed.backend.Records.Prestacion.Request.CreatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.DeshabilitarPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Response.CambioEstadoPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.CreatePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.GetPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionResponse;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TipoIndicacionPrestacionDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.IndicacionPrestacionMapper;
import com.accesmed.backend.Services.Mappers.PrestacionMapper;
import com.accesmed.backend.Services.QueryServices.IndicacionPrestacionQueryService;
import com.accesmed.backend.Services.QueryServices.PrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Prestación. Orquesta el flujo completo de los endpoints de prestaciones
 * (creación, actualización, transiciones de estado) validando reglas de negocio y
 * coordinando los services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionApp {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionDomainService prestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final EspecialidadDomainService especialidadDomainService;
    private final TipoIndicacionPrestacionDomainService tipoIndicacionPrestacionDomainService;
    private final PrestacionQueryService prestacionQueryService;
    private final IndicacionPrestacionQueryService indicacionPrestacionQueryService;
    private final PrestacionMapper prestacionMapper;
    private final IndicacionPrestacionMapper indicacionPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una prestación nueva con sus indicaciones anidadas (si las proporciona).
     * Nace en estado {@code NO_PUBLICADA}.
     *
     * @param createPrestacionRequest {@code CreatePrestacionRequest} datos de la prestación a crear
     * @return {@code CreatePrestacionResponse} la prestación creada, con sus indicaciones
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre ya existen, o si la especialidad no existe
     * @throws ValidacionException {@code ValidacionException} si las reglas de tolerancia de la prestación fallan
     */
    @Transactional
    public CreatePrestacionResponse createPrestacion(CreatePrestacionRequest createPrestacionRequest) {

        log.info("Creación de prestación iniciada: código={}", createPrestacionRequest.codigo());

        prestacionDomainService.validateCodigoPrestacionIsUnique(createPrestacionRequest.codigo());
        prestacionDomainService.validateNombrePrestacionIsUnique(createPrestacionRequest.nombre());

        prestacionDomainService.validateToleranciasPrestacion(
                createPrestacionRequest.duracionMinimaMinutos(),
                createPrestacionRequest.duracionMaximaMinutos(),
                createPrestacionRequest.tiempoToleranciaSolicitudMinutos(),
                createPrestacionRequest.tiempoToleranciaValidacionMinutos(),
                createPrestacionRequest.tiempoToleranciaReprogramacionMinutos(),
                createPrestacionRequest.tiempoToleranciaConfirmacionMinutos(),
                createPrestacionRequest.tiempoToleranciaCancelacionMinutos(),
                createPrestacionRequest.tiempoToleranciaAnuncioMinutos(),
                createPrestacionRequest.tiempoRecordatorioConfirmacionMinutos()
        );

        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadById(createPrestacionRequest.especialidadId());

        Prestacion prestacionNueva = prestacionMapper.toEntity(createPrestacionRequest);
        prestacionNueva.setEspecialidad(especialidadExistente);

        Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);
        prestacionDomainService.abrirTramoInicial(prestacionGuardada);

        List<GetIndicacionPrestacionResponse> indicacionesResponse = new ArrayList<>();
        if (createPrestacionRequest.indicaciones() != null && !createPrestacionRequest.indicaciones().isEmpty()) {
            List<IndicacionPrestacion> indicacionesNuevas = new ArrayList<>();

            for (CreateIndicacionPrestacionAnidadaRequest indicacionAnidada : createPrestacionRequest.indicaciones()) {
                TipoIndicacionPrestacion tipoIndicacionExistente = tipoIndicacionPrestacionDomainService
                        .findTipoIndicacionPrestacionById(indicacionAnidada.tipoIndicacionPrestacionId());

                IndicacionPrestacion indicacionNueva = indicacionPrestacionMapper.toEntity(indicacionAnidada);
                indicacionNueva.setNombre(indicacionAnidada.nombre());
                indicacionNueva.setDescripcion(indicacionAnidada.descripcion());
                indicacionNueva.setRequiereValidacion(indicacionAnidada.requiereValidacion());
                indicacionNueva.setPrestacion(prestacionGuardada);
                indicacionNueva.setTipoIndicacionPrestacion(tipoIndicacionExistente);

                indicacionesNuevas.add(indicacionNueva);
            }

            List<IndicacionPrestacion> indicacionesGuardadas = indicacionPrestacionDomainService
                    .saveIndicacionesPrestacion(indicacionesNuevas);

            indicacionesResponse = indicacionesGuardadas.stream()
                    .map(indicacionPrestacionMapper::toGetResponse)
                    .toList();
        }

        return prestacionMapper.toCreateResponse(prestacionGuardada, indicacionesResponse);

    }

    /**
     * Actualiza nombre y tolerancias/duraciones de una prestación. Se puede modificar en
     * cualquier estado.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updatePrestacionRequest {@code UpdatePrestacionRequest} datos a actualizar
     * @return {@code UpdatePrestacionResponse} la prestación actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el nombre es duplicado
     * @throws ValidacionException {@code ValidacionException} si las reglas de tolerancia fallan
     */
    @Transactional
    public UpdatePrestacionResponse updatePrestacion(UUID id, UpdatePrestacionRequest updatePrestacionRequest) {

        log.info("Actualización de prestación iniciada: id={}", id);

        Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(id);

        if (updatePrestacionRequest.nombre() != null) {
            prestacionDomainService.validateNombrePrestacionIsUnique(updatePrestacionRequest.nombre(), id);
        }

        prestacionDomainService.validateToleranciasPrestacion(
                orElseActual(updatePrestacionRequest.duracionMinimaMinutos(), prestacionExistente.getDuracionMinima()),
                orElseActual(updatePrestacionRequest.duracionMaximaMinutos(), prestacionExistente.getDuracionMaxima()),
                orElseActual(updatePrestacionRequest.tiempoToleranciaSolicitudMinutos(), prestacionExistente.getTiempoToleranciaSolicitud()),
                orElseActual(updatePrestacionRequest.tiempoToleranciaValidacionMinutos(), prestacionExistente.getTiempoToleranciaValidacion()),
                orElseActual(updatePrestacionRequest.tiempoToleranciaReprogramacionMinutos(), prestacionExistente.getTiempoToleranciaReprogramacion()),
                orElseActual(updatePrestacionRequest.tiempoToleranciaConfirmacionMinutos(), prestacionExistente.getTiempoToleranciaConfirmacion()),
                orElseActual(updatePrestacionRequest.tiempoToleranciaCancelacionMinutos(), prestacionExistente.getTiempoToleranciaCancelacion()),
                orElseActual(updatePrestacionRequest.tiempoToleranciaAnuncioMinutos(), prestacionExistente.getTiempoToleranciaAnuncio()),
                orElseActual(updatePrestacionRequest.tiempoRecordatorioConfirmacionMinutos(), prestacionExistente.getTiempoRecordatorioConfirmacion())
        );

        //TODO cascada AGEN: recalcular/dar de baja AgendaHorarios futuros libres afectados por el cambio de duraciones.

        prestacionMapper.updatePrestacion(prestacionExistente, updatePrestacionRequest);

        Prestacion prestacionActualizada = prestacionDomainService.savePrestacion(prestacionExistente);

        return prestacionMapper.toUpdateResponse(prestacionActualizada);

    }

    /**
     * Resuelve el valor en minutos a usar para revalidar la cadena de tolerancias:
     * el nuevo valor si vino en el request, o el valor actual de la prestación si no.
     *
     * @param minutosNuevos {@code Integer} valor nuevo, o {@code null} si no vino en el request
     * @param duracionActual {@code java.time.Duration} valor actual de la prestación
     * @return {@code Integer} el valor en minutos a usar para la revalidación
     */
    private Integer orElseActual(Integer minutosNuevos, java.time.Duration duracionActual) {
        return minutosNuevos != null ? minutosNuevos : (int) duracionActual.toMinutes();
    }

    /**
     * Publica una prestación (transición reversible {@code NO_PUBLICADA -> PUBLICADA}).
     * No exige médico asignado.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code CambioEstadoPrestacionResponse} la prestación publicada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no se puede publicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPrestacionResponse publicarPrestacion(UUID id) {

        log.info("Publicación de prestación iniciada: id={}", id);

        Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(id);

        prestacionDomainService.validatePuedePublicar(prestacionExistente);

        Prestacion prestacionPublicada = prestacionDomainService.abrirTramoEstado(prestacionExistente, EstadoPrestacion.PUBLICADA, null);

        return prestacionMapper.toCambioEstadoResponse(prestacionPublicada);

    }

    /**
     * Despublica una prestación (transición reversible {@code PUBLICADA -> NO_PUBLICADA}).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code CambioEstadoPrestacionResponse} la prestación despublicada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no se puede despublicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPrestacionResponse despublicarPrestacion(UUID id) {

        log.info("Despublicación de prestación iniciada: id={}", id);

        Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(id);

        prestacionDomainService.validatePuedeDespublicar(prestacionExistente);

        Prestacion prestacionDespublicada = prestacionDomainService.abrirTramoEstado(prestacionExistente, EstadoPrestacion.NO_PUBLICADA, null);

        return prestacionMapper.toCambioEstadoResponse(prestacionDespublicada);

    }

    /**
     * Deshabilita una prestación (transición terminal e irreversible = la baja del eje
     * "estados"). Restrictiva: rechaza si hay turnos vivos o agenda futura ocupada de esa
     * prestación.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param deshabilitarPrestacionRequest {@code DeshabilitarPrestacionRequest} motivo opcional
     * @return {@code CambioEstadoPrestacionResponse} la prestación deshabilitada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya está deshabilitada o tiene uso vigente
     */
    @Transactional
    public CambioEstadoPrestacionResponse deshabilitarPrestacion(UUID id, DeshabilitarPrestacionRequest deshabilitarPrestacionRequest) {

        log.info("Deshabilitación de prestación iniciada: id={}", id);

        Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(id);

        prestacionDomainService.validatePuedeDeshabilitar(prestacionExistente);
        prestacionDomainService.validateSinUsoVigente(prestacionExistente);

        //TODO cascada de escritura: cerrar MedicoPrestacion vigentes, bajar AgendaHorarios libres,
        // cerrar IndicacionPrestacion vigentes, bajar ObraSocialPlanPrestacion (módulos fuera de alcance).

        Prestacion prestacionDeshabilitada = prestacionDomainService.abrirTramoEstado(
                prestacionExistente, EstadoPrestacion.DESHABILITADA, deshabilitarPrestacionRequest.motivo());

        return prestacionMapper.toCambioEstadoResponse(prestacionDeshabilitada);

    }

    /**
     * Busca una prestación por su identificador, incluyendo sus indicaciones activas.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code GetPrestacionResponse} la prestación encontrada, con sus indicaciones
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     */
    @Transactional(readOnly = true)
    public GetPrestacionResponse findPrestacionById(UUID id) {

        log.info("Búsqueda de prestación iniciada: id={}", id);

        Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(id);

        List<IndicacionPrestacion> indicacionesActivas = indicacionPrestacionQueryService
                .findIndicacionesPrestacionByPrestacion(id);
        List<GetIndicacionPrestacionResponse> indicacionesResponse = indicacionesActivas.stream()
                .map(indicacionPrestacionMapper::toGetResponse)
                .toList();

        return prestacionMapper.toGetResponse(prestacionExistente, indicacionesResponse);

    }

    /**
     * Lista prestaciones según los filtros proporcionados.
     *
     * @param especialidadId {@code UUID} opcional, para filtrar por especialidad
     * @param estadoActual {@code EstadoPrestacion} opcional, para filtrar por estado
     * @return {@code List<ListPrestacionResponse>} lista de prestaciones que cumplen los filtros
     */
    @Transactional(readOnly = true)
    public List<ListPrestacionResponse> findPrestaciones(UUID especialidadId, EstadoPrestacion estadoActual) {

        log.info("Listado de prestaciones iniciado: especialidadId={}, estadoActual={}", especialidadId, estadoActual);

        List<Prestacion> prestaciones;

        if (especialidadId != null && estadoActual != null) {
            prestaciones = prestacionQueryService.findPrestacionesByEspecialidadAndEstadoActual(especialidadId, estadoActual);
        } else if (especialidadId != null) {
            prestaciones = prestacionQueryService.findPrestacionesByEspecialidad(especialidadId);
        } else if (estadoActual != null) {
            prestaciones = prestacionQueryService.findPrestacionesByEstadoActual(estadoActual);
        } else {
            prestaciones = prestacionQueryService.findAllPrestaciones();
        }

        return prestaciones.stream()
                .map(prestacionMapper::toListResponse)
                .toList();

    }

    //endregion

}
