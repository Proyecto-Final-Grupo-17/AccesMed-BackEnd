package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.CreateIndicacionPrestacionAnidadaRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.SoftDeleteIndicacionPrestacionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper para la entidad {@code IndicacionPrestacion}. Realiza conversiones entre
 * records de request/response y la entidad JPA.
 *
 * Nota: las FKs {@code prestacion} y {@code tipoIndicacionPrestacion} se ignoran en el mapeo
 * (la entidad tiene @Setter(AccessLevel.NONE) en ellas) y se setean en el App
 * después de validar su existencia.
 */
@Mapper(componentModel = "spring")
public interface IndicacionPrestacionMapper {

    /**
     * Convierte un {@code CreateIndicacionPrestacionAnidadaRequest} a una entidad {@code IndicacionPrestacion}.
     * Utilizado durante la creación anidada de indicaciones en una prestación.
     *
     * @param createIndicacionPrestacionAnidadaRequest {@code CreateIndicacionPrestacionAnidadaRequest} datos del request anidado
     * @return {@code IndicacionPrestacion} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "tipoIndicacionPrestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    IndicacionPrestacion toEntity(CreateIndicacionPrestacionAnidadaRequest createIndicacionPrestacionAnidadaRequest);

    /**
     * Convierte una lista de {@code CreateIndicacionPrestacionAnidadaRequest} a una lista
     * de entidades {@code IndicacionPrestacion}, reutilizando el mapeo singular. Utilizado
     * durante la creación anidada de indicaciones en una prestación.
     *
     * @param createIndicacionesPrestacionAnidadaRequest {@code List<CreateIndicacionPrestacionAnidadaRequest>} datos de los requests anidados
     * @return {@code List<IndicacionPrestacion>} entidades listas para persistir
     */
    List<IndicacionPrestacion> toEntities(List<CreateIndicacionPrestacionAnidadaRequest> createIndicacionesPrestacionAnidadaRequest);

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
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "tipoIndicacionPrestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
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
     * Convierte una entidad {@code IndicacionPrestacion} a {@code GetIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code GetIndicacionPrestacionResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "descripcion", source = "descripcion")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "tipoIndicacionPrestacion.nombre")
    GetIndicacionPrestacionResponse toGetResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code ListIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad
     * @return {@code ListIndicacionPrestacionResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "requiereValidacion", source = "requiereValidacion")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionId", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "tipoIndicacionPrestacionNombre", source = "tipoIndicacionPrestacion.nombre")
    ListIndicacionPrestacionResponse toListResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una lista de entidades {@code IndicacionPrestacion} a una lista de {@code GetIndicacionPrestacionResponse}.
     *
     * @param indicacionesPrestacion {@code List<IndicacionPrestacion>} lista de entidades
     * @return {@code List<GetIndicacionPrestacionResponse>} lista de respuestas
     */
    List<GetIndicacionPrestacionResponse> toGetResponses(List<IndicacionPrestacion> indicacionesPrestacion);

    /**
     * Convierte una entidad {@code IndicacionPrestacion} a {@code SoftDeleteIndicacionPrestacionResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad dada de baja
     * @return {@code SoftDeleteIndicacionPrestacionResponse} respuesta de baja lógica
     */
    SoftDeleteIndicacionPrestacionResponse toSoftDeleteResponse(IndicacionPrestacion indicacionPrestacion);

}
