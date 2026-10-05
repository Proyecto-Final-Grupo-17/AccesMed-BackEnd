package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.Especialidad.Request.CreateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Request.UpdateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Response.CreateEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.GetEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.ListEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.SoftDeleteEspecialidadResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper para la entidad {@code Especialidad}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 */
@Mapper(componentModel = "spring")
public interface EspecialidadMapper {

    /**
     * Convierte un {@code CreateEspecialidadRequest} a una entidad {@code Especialidad}.
     *
     * @param createEspecialidadRequest {@code CreateEspecialidadRequest} datos del request
     * @return {@code Especialidad} entidad lista para persistir
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
    Especialidad toEntity(CreateEspecialidadRequest createEspecialidadRequest);

    /**
     * Actualiza una especialidad existente con datos de {@code UpdateEspecialidadRequest}.
     * Un campo en {@code null} deja ese dato sin tocar.
     *
     * @param especialidad {@code Especialidad} entidad a actualizar
     * @param updateEspecialidadRequest {@code UpdateEspecialidadRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "updateEspecialidadRequest.codigo")
    @Mapping(target = "nombre", source = "updateEspecialidadRequest.nombre")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updateEspecialidad(@MappingTarget Especialidad especialidad, UpdateEspecialidadRequest updateEspecialidadRequest);

    /**
     * Convierte una entidad {@code Especialidad} a {@code CreateEspecialidadResponse}.
     *
     * @param especialidad {@code Especialidad} entidad
     * @return {@code CreateEspecialidadResponse} respuesta de creación
     */
    CreateEspecialidadResponse toCreateResponse(Especialidad especialidad);

    /**
     * Convierte una entidad {@code Especialidad} y sus datos de auditoría a
     * {@code GetEspecialidadResponse}.
     *
     * @param especialidad {@code Especialidad} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetEspecialidadResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "especialidad.id")
    @Mapping(target = "codigo", source = "especialidad.codigo")
    @Mapping(target = "nombre", source = "especialidad.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    GetEspecialidadResponse toGetResponse(Especialidad especialidad, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code Especialidad} a {@code GetEspecialidadResponse}
     * sin datos de auditoría (sobrecarga para compatibilidad con otros usos).
     *
     * @param especialidad {@code Especialidad} entidad
     * @return {@code GetEspecialidadResponse} respuesta de obtención con auditoria = null
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "auditoria", ignore = true)
    GetEspecialidadResponse toGetResponse(Especialidad especialidad);

    /**
     * Convierte una entidad {@code Especialidad} y sus datos de auditoría a
     * {@code ListEspecialidadResponse}.
     *
     * @param especialidad {@code Especialidad} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListEspecialidadResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "especialidad.id")
    @Mapping(target = "codigo", source = "especialidad.codigo")
    @Mapping(target = "nombre", source = "especialidad.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    ListEspecialidadResponse toListResponse(Especialidad especialidad, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code Especialidad} a {@code ListEspecialidadResponse}
     * sin datos de auditoría (sobrecarga para compatibilidad con otros usos).
     *
     * @param especialidad {@code Especialidad} entidad
     * @return {@code ListEspecialidadResponse} respuesta de listado con auditoria = null
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "auditoria", ignore = true)
    ListEspecialidadResponse toListResponse(Especialidad especialidad);

    /**
     * Convierte una lista de entidades {@code Especialidad} a una lista de {@code ListEspecialidadResponse}.
     *
     * @param especialidades {@code List<Especialidad>} lista de entidades
     * @return {@code List<ListEspecialidadResponse>} lista de respuestas
     */
    List<ListEspecialidadResponse> toListResponses(List<Especialidad> especialidades);

    /**
     * Convierte una entidad {@code Especialidad} a {@code SoftDeleteEspecialidadResponse}.
     *
     * @param especialidad {@code Especialidad} entidad dada de baja
     * @return {@code SoftDeleteEspecialidadResponse} respuesta de baja lógica
     */
    SoftDeleteEspecialidadResponse toSoftDeleteResponse(Especialidad especialidad);

    /**
     * Arma el {@code AuditoriaResponse} de una especialidad. {@code Especialidad} sí tiene
     * soft delete propio, así que {@code deletedAt}/{@code deletedBy}/{@code deletedReason}
     * se mapean normalmente.
     *
     * @param especialidad {@code Especialidad} entidad
     * @return {@code AuditoriaResponse} datos de auditoría de la especialidad
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(Especialidad especialidad);

}
