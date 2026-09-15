package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.UpdateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.CreateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.UpdateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.CreateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.ListTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.GetTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.SoftDeleteTipoIndicacionPrestacionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper para la entidad {@code TipoIndicacionPrestacion}. Realiza conversiones entre
 * records de request/response y la entidad JPA.
 */
@Mapper(componentModel = "spring")
public interface TipoIndicacionPrestacionMapper {

    /**
     * Convierte un {@code CreateTipoIndicacionPrestacionRequest} a una entidad {@code TipoIndicacionPrestacion}.
     *
     * @param createTipoIndicacionPrestacionRequest {@code CreateTipoIndicacionPrestacionRequest} datos del request
     * @return {@code TipoIndicacionPrestacion} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    TipoIndicacionPrestacion toEntity(CreateTipoIndicacionPrestacionRequest createTipoIndicacionPrestacionRequest);

    /**
     * Actualiza un tipo de indicación existente con datos de {@code UpdateTipoIndicacionPrestacionRequest}.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad a actualizar
     * @param updateTipoIndicacionPrestacionRequest {@code UpdateTipoIndicacionPrestacionRequest} datos del request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "updateTipoIndicacionPrestacionRequest.codigo")
    @Mapping(target = "nombre", source = "updateTipoIndicacionPrestacionRequest.nombre")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updateTipoIndicacionPrestacion(@MappingTarget TipoIndicacionPrestacion tipoIndicacionPrestacion,
                                        UpdateTipoIndicacionPrestacionRequest updateTipoIndicacionPrestacionRequest);

    /**
     * Convierte una entidad {@code TipoIndicacionPrestacion} a {@code CreateTipoIndicacionPrestacionResponse}.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad
     * @return {@code CreateTipoIndicacionPrestacionResponse} respuesta de creación
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    CreateTipoIndicacionPrestacionResponse toCreateResponse(TipoIndicacionPrestacion tipoIndicacionPrestacion);

    /**
     * Convierte una entidad {@code TipoIndicacionPrestacion} a {@code UpdateTipoIndicacionPrestacionResponse}.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad
     * @return {@code UpdateTipoIndicacionPrestacionResponse} respuesta de actualización
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    UpdateTipoIndicacionPrestacionResponse toUpdateResponse(TipoIndicacionPrestacion tipoIndicacionPrestacion);

    /**
     * Convierte una entidad {@code TipoIndicacionPrestacion} a {@code GetTipoIndicacionPrestacionResponse}.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetTipoIndicacionPrestacionResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "codigo", source = "tipoIndicacionPrestacion.codigo")
    @Mapping(target = "nombre", source = "tipoIndicacionPrestacion.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    GetTipoIndicacionPrestacionResponse toGetResponse(TipoIndicacionPrestacion tipoIndicacionPrestacion, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code TipoIndicacionPrestacion} a {@code ListTipoIndicacionPrestacionResponse}.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListTipoIndicacionPrestacionResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "tipoIndicacionPrestacion.id")
    @Mapping(target = "codigo", source = "tipoIndicacionPrestacion.codigo")
    @Mapping(target = "nombre", source = "tipoIndicacionPrestacion.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    ListTipoIndicacionPrestacionResponse toListResponse(TipoIndicacionPrestacion tipoIndicacionPrestacion, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code TipoIndicacionPrestacion} a {@code SoftDeleteTipoIndicacionPrestacionResponse}.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad dada de baja
     * @return {@code SoftDeleteTipoIndicacionPrestacionResponse} respuesta de baja lógica
     */
    SoftDeleteTipoIndicacionPrestacionResponse toSoftDeleteResponse(TipoIndicacionPrestacion tipoIndicacionPrestacion);

    /**
     * Arma el {@code AuditoriaResponse} de un tipo de indicación de prestación.
     * {@code TipoIndicacionPrestacion} tiene soft delete propio, así que {@code deletedAt}/
     * {@code deletedBy}/{@code deletedReason} se mapean normalmente.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad
     * @return {@code AuditoriaResponse} datos de auditoría del tipo de indicación
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(TipoIndicacionPrestacion tipoIndicacionPrestacion);

}
