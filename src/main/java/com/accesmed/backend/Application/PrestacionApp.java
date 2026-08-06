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
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionResponse;
import com.accesmed.backend.Services.DomainServices.*;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.IndicacionPrestacionMapper;
import com.accesmed.backend.Services.Mappers.PrestacionMapper;
import com.accesmed.backend.Services.QueryServices.PrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
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

    //region ========== Dependencias ==========

    //Domain Services
    private final PrestacionDomainService prestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final EspecialidadDomainService especialidadDomainService;
    private final TipoIndicacionPrestacionDomainService tipoIndicacionPrestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final TurnoDomainService turnoDomainService;
    private final AgendaHorariosDomainService agendaHorariosDomainService;

    //Mappers
    private final PrestacionMapper prestacionMapper;
    private final IndicacionPrestacionMapper indicacionPrestacionMapper;

    //Query Services
    private final PrestacionQueryService prestacionQueryService;
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

        //Validar que el código sea único para una prestación activa.
        prestacionDomainService.validateCodigoPrestacionIsUnique(createPrestacionRequest.codigo());

        //Validar que el nombre sea único para una prestación activa.
        prestacionDomainService.validateNombrePrestacionIsUnique(createPrestacionRequest.nombre());

        //Validar tolerancias.
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

        //Validar que la especialidad existe y este activa
        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadActivaById(createPrestacionRequest.especialidadId());

        //Mapear nueva Prestacion y setear su Especialiad
        Prestacion prestacionNueva = prestacionMapper.toEntity(createPrestacionRequest);
        prestacionNueva.setEspecialidad(especialidadExistente);

        //Setear estado inicial
        historicoEstadoPrestacionDomainService.setInitialEstadoForNewPrestacion(prestacionNueva);

        //Guardar Prestacion
        Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);

        //Mapear las indicaciones anidadas a entidades
        List<CreateIndicacionPrestacionAnidadaRequest> indicacionesRequest = createPrestacionRequest.indicaciones();
        List<IndicacionPrestacion> indicacionesNuevas = new ArrayList<>();
        if (indicacionesRequest != null && !indicacionesRequest.isEmpty()) {
            indicacionesNuevas = indicacionPrestacionMapper.toEntities(indicacionesRequest);

            //Resolver las relaciones que el mapper no puede resolver (requieren búsqueda por id)
            for (int i = 0; i < indicacionesNuevas.size(); i++) {
                TipoIndicacionPrestacion tipoIndicacionExistente = tipoIndicacionPrestacionDomainService
                        .findTipoIndicacionPrestacionActivoById(indicacionesRequest.get(i).tipoIndicacionPrestacionId());

                indicacionesNuevas.get(i).setPrestacion(prestacionGuardada);
                indicacionesNuevas.get(i).setTipoIndicacionPrestacion(tipoIndicacionExistente);
            }
        }

        //Guardar las indicaciones mapeadas
        List<GetIndicacionPrestacionResponse> indicacionesResponse = new ArrayList<>();
        if (!indicacionesNuevas.isEmpty()) {
            List<IndicacionPrestacion> indicacionesGuardadas = indicacionPrestacionDomainService
                    .saveIndicacionesPrestacion(indicacionesNuevas);

            //Mapear la respuesta de indicaciones
            indicacionesResponse = indicacionesGuardadas.stream()
                    .map(indicacionPrestacionMapper::toGetResponse)
                    .toList();
        }

        //Devolver response mapeado
        CreatePrestacionResponse createPrestacionResponse = prestacionMapper.toCreateResponse(prestacionGuardada, indicacionesResponse);
        return createPrestacionResponse;

    }

    /**
     * Actualiza nombre y tolerancias/duraciones de una prestación. Se puede modificar en
     * cualquier estado salvo {@code DESHABILITADA} (terminal e irreversible).
     *
     * @param updatePrestacionRequest {@code UpdatePrestacionRequest} datos a actualizar, incluyendo
     *        el id de la prestación (ya validado contra la ruta en el Controller)
     * @return {@code UpdatePrestacionResponse} la prestación actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe o está deshabilitada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el nombre es duplicado
     * @throws ValidacionException {@code ValidacionException} si las reglas de tolerancia fallan
     */
    @Transactional
    public UpdatePrestacionResponse updatePrestacion(UpdatePrestacionRequest updatePrestacionRequest) {

        UUID id = updatePrestacionRequest.id();

        log.info("Actualización de prestación iniciada: id={}", id);

        //Buscar la prestación activa (deshabilitada es terminal: no admite más cambios)
        Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaById(id);

        //Validar que el nombre sea único, si vino en el request
        if (updatePrestacionRequest.nombre() != null) {
            prestacionDomainService.validateNombrePrestacionIsUnique(updatePrestacionRequest.nombre(), id);
        }

        //Validar tolerancias, revalidando la cadena completa con los valores actuales como respaldo
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

        //Aplicar los cambios del request sobre la entidad existente
        prestacionMapper.updatePrestacion(prestacionExistente, updatePrestacionRequest);

        //Guardar la prestación actualizada
        Prestacion prestacionActualizada = prestacionDomainService.savePrestacion(prestacionExistente);

        //Devolver response mapeado
        UpdatePrestacionResponse updatePrestacionResponse = prestacionMapper.toUpdateResponse(prestacionActualizada);
        return updatePrestacionResponse;

    }


    /**
     * Publica una prestación (transición reversible {@code NO_PUBLICADA -> PUBLICADA}).
     * No exige médico asignado.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code CambioEstadoPrestacionResponse} la prestación publicada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación no existe,
     *         ya está deshabilitada, o no se puede publicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPrestacionResponse publishPrestacion(UUID id) {

        log.info("Publicación de prestación iniciada: id={}", id);

        //Transicionar el estado a PUBLICADA
        Prestacion prestacionPublicada = historicoEstadoPrestacionDomainService.changeEstadoPrestacion(id, EstadoPrestacion.PUBLICADA, null);

        //Devolver response mapeado
        CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionMapper.toCambioEstadoResponse(prestacionPublicada);
        return cambioEstadoPrestacionResponse;

    }

    /**
     * Despublica una prestación (transición reversible {@code PUBLICADA -> NO_PUBLICADA}).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code CambioEstadoPrestacionResponse} la prestación despublicada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación no existe,
     *         ya está deshabilitada, o no se puede despublicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPrestacionResponse unpublishPrestacion(UUID id) {

        log.info("Despublicación de prestación iniciada: id={}", id);

        //Transicionar el estado a NO_PUBLICADA
        Prestacion prestacionDespublicada = historicoEstadoPrestacionDomainService.changeEstadoPrestacion(id, EstadoPrestacion.NO_PUBLICADA, null);

        //Devolver response mapeado
        CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionMapper.toCambioEstadoResponse(prestacionDespublicada);
        return cambioEstadoPrestacionResponse;

    }

    /**
     * Deshabilita una prestación (transición terminal e irreversible = la baja del eje
     * "estados"). Restrictiva: rechaza si hay turnos vivos o agenda futura ocupada de esa
     * prestación.
     *
     * @param deshabilitarPrestacionRequest {@code DeshabilitarPrestacionRequest} motivo opcional,
     *        incluyendo el id de la prestación (ya validado contra la ruta en el Controller)
     * @return {@code CambioEstadoPrestacionResponse} la prestación deshabilitada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación no existe,
     *         ya está deshabilitada, o tiene turnos vivos o agenda futura ocupada
     */
    @Transactional
    public CambioEstadoPrestacionResponse disablePrestacion(DeshabilitarPrestacionRequest deshabilitarPrestacionRequest) {

        UUID id = deshabilitarPrestacionRequest.id();

        log.info("Deshabilitación de prestación iniciada: id={}", id);

        //Validar que no tenga turnos vivos
        turnoDomainService.validateSinTurnosVivos(id);

        //Validar que no tenga agenda futura ocupada
        agendaHorariosDomainService.validateSinAgendaFuturaOcupada(id);

        //Cerrar la vigencia de las indicaciones vigentes: sin turnos vivos (ya validado arriba),
        //"ahora" nunca puede caer antes de un turno que necesite protección.
        indicacionPrestacionDomainService.cerrarVigenciaIndicacionesPrestacionByPrestacion(id, ZonedDateTime.now());

        //TODO cascada de escritura: cerrar MedicoPrestacion vigentes, bajar AgendaHorarios libres,
        // bajar ObraSocialPlanPrestacion (módulos fuera de alcance).

        //Transicionar el estado a DESHABILITADA
        Prestacion prestacionDeshabilitada = historicoEstadoPrestacionDomainService.changeEstadoPrestacion(
                id, EstadoPrestacion.DESHABILITADA, deshabilitarPrestacionRequest.motivo());

        //Devolver response mapeado
        CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionMapper.toCambioEstadoResponse(prestacionDeshabilitada);
        return cambioEstadoPrestacionResponse;

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

        //Buscar prestaciones según los filtros proporcionados
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

        //Devolver response mapeado
        List<ListPrestacionResponse> listPrestacionResponse = prestaciones.stream()
                .map(prestacionMapper::toListResponse)
                .toList();
        return listPrestacionResponse;

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


    //endregion

}
