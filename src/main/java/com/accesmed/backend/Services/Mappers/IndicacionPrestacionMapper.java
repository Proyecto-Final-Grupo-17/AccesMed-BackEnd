package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.CreateIndicacionPrestacionAnidadaRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ScheduleBajaIndicacionPrestacionResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mapper para la entidad {@code IndicacionPrestacion}. Realiza conversiones entre
 * records de request/response y la entidad JPA.
 *
 * Nota: las FKs {@code prestacion} y {@code tipoIndicacionPrestacion} no pueden resolverse
 * con datos propios del request (requieren búsqueda por id). El App las busca y valida su
 * existencia primero, y se las pasa al mapper como parámetros {@code @Context}: la
 * {@code Prestacion} ya guardada y un {@code Map} de {@code TipoIndicacionPrestacion} ya
 * validados, indexado por id. Así el mapeo de la lista completa (entidad y FKs) queda en
 * el mapper y el App no itera manualmente.
 */
@Mapper(componentModel = "spring")
public interface IndicacionPrestacionMapper {

    /**
     * Convierte un {@code CreateIndicacionPrestacionAnidadaRequest} a una entidad {@code IndicacionPrestacion}.
     * Utilizado durante la creación anidada de indicaciones en una prestación.
     *
     * @param createIndicacionPrestacionAnidadaRequest {@code CreateIndicacionPrestacionAnidadaRequest} datos del request anidado
     * @param prestacion {@code Prestacion} prestación ya guardada a la que pertenece la indicación
     * @param tiposIndicacionPorId {@code Map<UUID, TipoIndicacionPrestacion>} tipos de indicación ya
     *        validados por el App, indexados por id
     * @return {@code IndicacionPrestacion} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nombre", source = "createIndicacionPrestacionAnidadaRequest.nombre")
    @Mapping(target = "descripcion", source = "createIndicacionPrestacionAnidadaRequest.descripcion")
    @Mapping(target = "requiereValidacion", source = "createIndicacionPrestacionAnidadaRequest.requiereValidacion")
    @Mapping(target = "fechaInicioVigencia", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "fechaFinVigencia", ignore = true)
    @Mapping(target = "prestacion", expression = "java(prestacion)")
    @Mapping(target = "tipoIndicacionPrestacion",
            expression = "java(tiposIndicacionPorId.get(createIndicacionPrestacionAnidadaRequest.tipoIndicacionPrestacionId()))")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    IndicacionPrestacion toEntity(CreateIndicacionPrestacionAnidadaRequest createIndicacionPrestacionAnidadaRequest,
                                   @Context Prestacion prestacion,
                                   @Context Map<UUID, TipoIndicacionPrestacion> tiposIndicacionPorId);

    /**
     * Convierte una lista de {@code CreateIndicacionPrestacionAnidadaRequest} a una lista
     * de entidades {@code IndicacionPrestacion}, reutilizando el mapeo singular para cada
     * elemento (incluida la resolución de FKs vía contexto). Utilizado durante la creación
     * anidada de indicaciones en una prestación.
     *
     * @param createIndicacionesPrestacionAnidadaRequest {@code List<CreateIndicacionPrestacionAnidadaRequest>} datos de los requests anidados
     * @param prestacion {@code Prestacion} prestación ya guardada a la que pertenecen las indicaciones
     * @param tiposIndicacionPorId {@code Map<UUID, TipoIndicacionPrestacion>} tipos de indicación ya
     *        validados por el App, indexados por id
     * @return {@code List<IndicacionPrestacion>} entidades listas para persistir
     */
    List<IndicacionPrestacion> toEntities(List<CreateIndicacionPrestacionAnidadaRequest> createIndicacionesPrestacionAnidadaRequest,
                                           @Context Prestacion prestacion,
                                           @Context Map<UUID, TipoIndicacionPrestacion> tiposIndicacionPorId);

    /**
     * Actualiza una indicación existente con datos de {@code UpdateIndicacionPrestacionRequest}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad a actualizar
     * @param updateIndicacionPrestacionRequest {@code UpdateIndicacionPrestacionRequest} datos del request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nombre", source = "updateIndicacionPrestacionRequest.nombre")
    @Mapping(target = "descripcion", source = "updateIndicacionPrestacionRequest.descripcion")
    @Mapping(target = "requiereValidacion", source = "updateIndicacionPrestacionRequest.requiereValidacion")
    @Mapping(target = "fechaInicioVigencia", ignore = true)
    @Mapping(target = "fechaFinVigencia", ignore = true)
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "tipoIndicacionPrestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updateIndicacionPrestacion(@MappingTarget IndicacionPrestacion indicacionPrestacion,
                                    UpdateIndicacionPrestacionRequest updateIndicacionPrestacionRequest);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code CreateIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code CreateIndicacionPrestacionResponse} respuesta de creación
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "tipoIndicacionPrestacion.nombre")
    CreateIndicacionPrestacionResponse toCreateResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una lista de entidades {@code IndicacionPrestacion} a una lista de
     * {@code CreateIndicacionPrestacionResponse}, reutilizando el mapeo singular.
     *
     * @param indicacionesPrestacion {@code List<IndicacionPrestacion>} lista de entidades
     * @return {@code List<CreateIndicacionPrestacionResponse>} lista de respuestas de creación
     */
    List<CreateIndicacionPrestacionResponse> toCreateResponses(List<IndicacionPrestacion> indicacionesPrestacion);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code UpdateIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code UpdateIndicacionPrestacionResponse} respuesta de actualización
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "tipoIndicacionPrestacion.nombre")
    UpdateIndicacionPrestacionResponse toUpdateResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} y sus datos de auditoría a
     * {@code GetIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetIndicacionPrestacionResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "indicacionPrestacion.id")
    @Mapping(target = "nombre", source = "indicacionPrestacion.nombre")
    @Mapping(target = "descripcion", source = "indicacionPrestacion.descripcion")
    @Mapping(target = "requiereValidacion", source = "indicacionPrestacion.requiereValidacion")
    @Mapping(target = "prestacionId", source = "indicacionPrestacion.prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "indicacionPrestacion.tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "indicacionPrestacion.tipoIndicacionPrestacion.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    GetIndicacionPrestacionResponse toGetResponse(IndicacionPrestacion indicacionPrestacion, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code GetIndicacionPrestacionResponse}
     * sin datos de auditoría (sobrecarga para compatibilidad con embedidos).
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code GetIndicacionPrestacionResponse} respuesta de obtención con auditoria = null
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "tipoIndicacionPrestacion.nombre")
    @Mapping(target = "auditoria", ignore = true)
    GetIndicacionPrestacionResponse toGetResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} y sus datos de auditoría a
     * {@code ListIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListIndicacionPrestacionResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "indicacionPrestacion.id")
    @Mapping(target = "nombre", source = "indicacionPrestacion.nombre")
    @Mapping(target = "requiereValidacion", source = "indicacionPrestacion.requiereValidacion")
    @Mapping(target = "prestacionId", source = "indicacionPrestacion.prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "indicacionPrestacion.tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "indicacionPrestacion.tipoIndicacionPrestacion.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    ListIndicacionPrestacionResponse toListResponse(IndicacionPrestacion indicacionPrestacion, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code ListIndicacionPrestacionResponse}
     * sin datos de auditoría (sobrecarga para compatibilidad con otros usos).
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code ListIndicacionPrestacionResponse} respuesta de listado con auditoria = null
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "tipoIndicacionPrestacion.nombre")
    @Mapping(target = "auditoria", ignore = true)
    ListIndicacionPrestacionResponse toListResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una lista de entidades {@code IndicacionPrestacion} a una lista de {@code GetIndicacionPrestacionResponse}.
     *
     * @param indicacionesPrestacion {@code List<IndicacionPrestacion>} lista de entidades
     * @return {@code List<GetIndicacionPrestacionResponse>} lista de respuestas
     */
    List<GetIndicacionPrestacionResponse> toGetResponses(List<IndicacionPrestacion> indicacionesPrestacion);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code ScheduleBajaIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad con la vigencia cerrada
     * @return {@code ScheduleBajaIndicacionPrestacionResponse} respuesta de la baja programada
     */
    ScheduleBajaIndicacionPrestacionResponse toScheduleBajaResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Arma el {@code AuditoriaResponse} de una indicación de prestación. {@code IndicacionPrestacion}
     * no tiene soft delete propio (se retira cerrando vigencia), así que {@code deletedAt}/
     * {@code deletedBy}/{@code deletedReason} siempre viajan en {@code null}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code AuditoriaResponse} datos de auditoría de la indicación
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    AuditoriaResponse toAuditoria(IndicacionPrestacion indicacionPrestacion);

}
