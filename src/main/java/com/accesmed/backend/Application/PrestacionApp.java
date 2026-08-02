package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Request.CreateIndicacionPrestacionAnidadaRequest;
import com.accesmed.backend.Records.Prestacion.Request.CreatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdatePrestacionNoHabilitadaRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdateToleranciasPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Response.CreatePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.EnablePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.GetPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.SoftDeletePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionNoHabilitadaResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdateToleranciasPrestacionResponse;
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
 * (creación, actualización, baja, habilitación) validando reglas de negocio y coordinando
 * los services.
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
     *
     * @param createPrestacionRequest {@code CreatePrestacionRequest} datos de la prestación a crear
     * @return {@code CreatePrestacionResponse} la prestación creada, con sus indicaciones
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre ya existen, o si la especialidad no existe
     * @throws ValidacionException {@code ValidacionException} si las reglas de tolerancia de la prestación fallan
     */
    @Transactional
    public CreatePrestacionResponse createPrestacion(CreatePrestacionRequest createPrestacionRequest) {

        log.info("Creación de prestación iniciada: código={}", createPrestacionRequest.codigo());

        //Validar que el código no esté repetido entre las prestaciones activas
        prestacionDomainService.validateCodigoPrestacionIsUnique(createPrestacionRequest.codigo());

        //Validar que el nombre no esté repetido entre las prestaciones activas
        prestacionDomainService.validateNombrePrestacionIsUnique(createPrestacionRequest.nombre());

        //Validar las reglas de tolerancia y duraciones
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

        //Buscar la especialidad para asignarla a la prestación
        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadById(createPrestacionRequest.especialidadId());

        //Mapear el request a entidad y asignar la especialidad
        Prestacion prestacionNueva = prestacionMapper.toEntity(createPrestacionRequest);
        prestacionNueva.setEspecialidad(especialidadExistente);

        //Persistir la prestación
        Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);

        //Si hay indicaciones anidadas, crearlas y asociarlas
        List<GetIndicacionPrestacionResponse> indicacionesResponse = new ArrayList<>();
        if (createPrestacionRequest.indicaciones() != null && !createPrestacionRequest.indicaciones().isEmpty()) {
            List<IndicacionPrestacion> indicacionesNuevas = new ArrayList<>();

            for (CreateIndicacionPrestacionAnidadaRequest indicacionAnidada : createPrestacionRequest.indicaciones()) {
                //Buscar el tipo de indicación
                TipoIndicacionPrestacion tipoIndicacionExistente = tipoIndicacionPrestacionDomainService
                        .findTipoIndicacionPrestacionById(indicacionAnidada.tipoIndicacionPrestacionId());

                //Mapear a entidad y asignar prestación y tipo
                IndicacionPrestacion indicacionNueva = indicacionPrestacionMapper.toEntity(indicacionAnidada);
                indicacionNueva.setNombre(indicacionAnidada.nombre());
                indicacionNueva.setDescripcion(indicacionAnidada.descripcion());
                indicacionNueva.setRequiereValidacion(indicacionAnidada.requiereValidacion());
                indicacionNueva.setPrestacion(prestacionGuardada);
                indicacionNueva.setTipoIndicacionPrestacion(tipoIndicacionExistente);

                indicacionesNuevas.add(indicacionNueva);
            }

            //Guardar todas las indicaciones juntas
            List<IndicacionPrestacion> indicacionesGuardadas = indicacionPrestacionDomainService
                    .saveIndicacionesPrestacion(indicacionesNuevas);

            //Mapear a respuesta
            indicacionesResponse = indicacionesGuardadas.stream()
                    .map(indicacionPrestacionMapper::toGetResponse)
                    .toList();
        }

        //Armar y devolver el response
        return prestacionMapper.toCreateResponse(prestacionGuardada, indicacionesResponse);

    }

    /**
     * Actualiza los datos generales (nombre, especialidad) de una prestación en borrador.
     * Solo aplicable mientras la prestación no esté habilitada.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updatePrestacionNoHabilitadaRequest {@code UpdatePrestacionNoHabilitadaRequest} datos a actualizar
     * @return {@code UpdatePrestacionNoHabilitadaResponse} la prestación actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación o especialidad no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el nombre es duplicado o la prestación ya está habilitada
     */
    @Transactional
    public UpdatePrestacionNoHabilitadaResponse updatePrestacionNoHabilitada(UUID id,
            UpdatePrestacionNoHabilitadaRequest updatePrestacionNoHabilitadaRequest) {

        log.info("Actualización de datos generales de prestación iniciada: id={}", id);

        //Buscar la prestación existente
        Prestacion prestacionExistente = prestacionDomainService.findActivePrestacionById(id);

        //Validar que la prestación esté en borrador: no admite esta operación si ya está habilitada
        prestacionDomainService.validatePrestacionIsBorrador(prestacionExistente);

        //Validar que el nombre no esté repetido, excluyendo el id actual, si vino en el request
        if (updatePrestacionNoHabilitadaRequest.nombre() != null) {
            prestacionDomainService.validateNombrePrestacionIsUnique(updatePrestacionNoHabilitadaRequest.nombre(), id);
        }

        //Actualizar el nombre usando el mapper (ignora null)
        prestacionMapper.updatePrestacionNoHabilitada(prestacionExistente, updatePrestacionNoHabilitadaRequest);

        //Buscar y asignar la especialidad, si vino en el request
        if (updatePrestacionNoHabilitadaRequest.especialidadId() != null) {
            Especialidad especialidadExistente = especialidadDomainService
                    .findEspecialidadById(updatePrestacionNoHabilitadaRequest.especialidadId());
            prestacionExistente.setEspecialidad(especialidadExistente);
        }

        //Persistir cambios
        Prestacion prestacionActualizada = prestacionDomainService.savePrestacion(prestacionExistente);

        //Armar y devolver el response
        return prestacionMapper.toUpdatePrestacionNoHabilitadaResponse(prestacionActualizada);

    }

    /**
     * Actualiza las duraciones y tolerancias de una prestación. Es lo único editable una
     * vez que la prestación está habilitada; mientras está en borrador también se edita
     * por acá.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updateToleranciasPrestacionRequest {@code UpdateToleranciasPrestacionRequest} datos a actualizar
     * @return {@code UpdateToleranciasPrestacionResponse} la prestación actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     * @throws ValidacionException {@code ValidacionException} si las reglas de tolerancia fallan
     */
    @Transactional
    public UpdateToleranciasPrestacionResponse updateToleranciasPrestacion(UUID id,
            UpdateToleranciasPrestacionRequest updateToleranciasPrestacionRequest) {

        log.info("Actualización de tolerancias de prestación iniciada: id={}", id);

        //Buscar la prestación existente
        Prestacion prestacionExistente = prestacionDomainService.findActivePrestacionById(id);

        //Revalidar la cadena de tolerancias con el resultado de aplicar el request sobre los valores actuales
        prestacionDomainService.validateToleranciasPrestacion(
                orElseActual(updateToleranciasPrestacionRequest.duracionMinimaMinutos(), prestacionExistente.getDuracionMinima()),
                orElseActual(updateToleranciasPrestacionRequest.duracionMaximaMinutos(), prestacionExistente.getDuracionMaxima()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoToleranciaSolicitudMinutos(), prestacionExistente.getTiempoToleranciaSolicitud()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoToleranciaValidacionMinutos(), prestacionExistente.getTiempoToleranciaValidacion()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoToleranciaReprogramacionMinutos(), prestacionExistente.getTiempoToleranciaReprogramacion()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoToleranciaConfirmacionMinutos(), prestacionExistente.getTiempoToleranciaConfirmacion()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoToleranciaCancelacionMinutos(), prestacionExistente.getTiempoToleranciaCancelacion()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoToleranciaAnuncioMinutos(), prestacionExistente.getTiempoToleranciaAnuncio()),
                orElseActual(updateToleranciasPrestacionRequest.tiempoRecordatorioConfirmacionMinutos(), prestacionExistente.getTiempoRecordatorioConfirmacion())
        );

        //Actualizar las tolerancias usando el mapper (ignora null)
        prestacionMapper.updateToleranciasPrestacion(prestacionExistente, updateToleranciasPrestacionRequest);

        //Persistir cambios
        Prestacion prestacionActualizada = prestacionDomainService.savePrestacion(prestacionExistente);

        //Armar y devolver el response
        return prestacionMapper.toUpdateToleranciasResponse(prestacionActualizada);

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
     * Habilita una prestación (cambio irreversible de estado de borrador a habilitada).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code EnablePrestacionResponse} la prestación habilitada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya está habilitada
     * @throws ValidacionException {@code ValidacionException} si las reglas de tolerancia fallan
     */
    @Transactional
    public EnablePrestacionResponse habilitarPrestacion(UUID id) {

        log.info("Habilitación de prestación iniciada: id={}", id);

        //Buscar la prestación
        Prestacion prestacionExistente = prestacionDomainService.findActivePrestacionById(id);

        //Validar que esté en borrador
        prestacionDomainService.validatePrestacionIsBorrador(prestacionExistente);

        //Revalidar las reglas de tolerancia con los valores actuales (convertir Duration → minutos)
        prestacionDomainService.validateToleranciasPrestacion(
                (int) prestacionExistente.getDuracionMinima().toMinutes(),
                (int) prestacionExistente.getDuracionMaxima().toMinutes(),
                (int) prestacionExistente.getTiempoToleranciaSolicitud().toMinutes(),
                (int) prestacionExistente.getTiempoToleranciaValidacion().toMinutes(),
                (int) prestacionExistente.getTiempoToleranciaReprogramacion().toMinutes(),
                (int) prestacionExistente.getTiempoToleranciaConfirmacion().toMinutes(),
                (int) prestacionExistente.getTiempoToleranciaCancelacion().toMinutes(),
                (int) prestacionExistente.getTiempoToleranciaAnuncio().toMinutes(),
                (int) prestacionExistente.getTiempoRecordatorioConfirmacion().toMinutes()
        );

        //Habilitar
        Prestacion prestacionHabilitada = prestacionDomainService.habilitarPrestacion(prestacionExistente);

        //Devolver el response
        return prestacionMapper.toEnableResponse(prestacionHabilitada);

    }

    /**
     * Da de baja una prestación (baja lógica), eliminando primero sus indicaciones.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code SoftDeletePrestacionResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación no existe
     */
    @Transactional
    public SoftDeletePrestacionResponse softDeletePrestacion(UUID id) {

        log.info("Baja de prestación iniciada: id={}", id);

        //Buscar la prestación
        Prestacion prestacionExistente = prestacionDomainService.findActivePrestacionById(id);

        //TODO cascada TURN/AGEN/MEDPREST: la baja de una prestación debe cancelar los turnos futuros activos,
        // dar de baja los AgendaHorarios futuros y las MedicoPrestacion. Se implementa cuando existan esos módulos.

        //Dar de baja todas las indicaciones activas de la prestación
        indicacionPrestacionDomainService.softDeleteIndicacionesPrestacionByPrestacion(id, "Baja de prestación");

        //Dar de baja la prestación
        prestacionDomainService.softDeletePrestacion(prestacionExistente, "Baja de prestación");

        //Devolver el response
        return prestacionMapper.toSoftDeleteResponse(prestacionExistente);

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

        //Buscar la prestación
        Prestacion prestacionExistente = prestacionDomainService.findActivePrestacionById(id);

        //Obtener sus indicaciones activas
        List<IndicacionPrestacion> indicacionesActivas = indicacionPrestacionQueryService
                .findIndicacionesPrestacionByPrestacion(id);
        List<GetIndicacionPrestacionResponse> indicacionesResponse = indicacionesActivas.stream()
                .map(indicacionPrestacionMapper::toGetResponse)
                .toList();

        //Armar y devolver el response
        return prestacionMapper.toGetResponse(prestacionExistente, indicacionesResponse);

    }

    /**
     * Lista prestaciones según los filtros proporcionados.
     *
     * @param especialidadId {@code UUID} opcional, para filtrar por especialidad
     * @param habilitadas {@code Boolean} opcional. {@code null} lista todas,
     *                   {@code true} lista solo habilitadas, {@code false} lista solo en borrador
     * @return {@code List<ListPrestacionResponse>} lista de prestaciones que cumplen los filtros
     */
    @Transactional(readOnly = true)
    public List<ListPrestacionResponse> findPrestaciones(UUID especialidadId, Boolean habilitadas) {

        log.info("Listado de prestaciones iniciado: especialidadId={}, habilitadas={}", especialidadId, habilitadas);

        List<Prestacion> prestaciones;

        //Aplicar los filtros según lo proporcionado
        if (especialidadId != null && habilitadas != null) {
            //Filtro por especialidad y estado
            if (habilitadas) {
                prestaciones = prestacionQueryService.findPrestacionesHabilitadasByEspecialidad(especialidadId);
            } else {
                prestaciones = prestacionQueryService.findPrestacionesEnBorradorByEspecialidad(especialidadId);
            }
        } else if (especialidadId != null) {
            //Solo filtro por especialidad
            prestaciones = prestacionQueryService.findPrestacionesByEspecialidad(especialidadId);
        } else if (habilitadas != null) {
            //Solo filtro por estado
            if (habilitadas) {
                prestaciones = prestacionQueryService.findPrestacionesHabilitadas();
            } else {
                prestaciones = prestacionQueryService.findPrestacionesEnBorrador();
            }
        } else {
            //Sin filtros
            prestaciones = prestacionQueryService.findAllPrestaciones();
        }

        //Mapear a response
        return prestaciones.stream()
                .map(prestacionMapper::toListResponse)
                .toList();

    }

    //endregion

}
