package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.ObraSocial.Request.CreateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Request.UpdateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Response.CreateObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetPlanAnidadoResponse;
import com.accesmed.backend.Records.ObraSocial.Response.ListObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.SoftDeleteObraSocialResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper para la entidad {@code ObraSocial}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: los planes anidados se arman en el {@code ObraSocialApp} (vía {@code PlanMapper})
 * y se reciben ya mapeados en los métodos de respuesta.
 */
@Mapper(componentModel = "spring")
public interface ObraSocialMapper {

    /**
     * Convierte un {@code CreateObraSocialRequest} a una entidad {@code ObraSocial}.
     *
     * @param createObraSocialRequest {@code CreateObraSocialRequest} datos del request
     * @return {@code ObraSocial} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "razonSocial", source = "razonSocial")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    ObraSocial toEntity(CreateObraSocialRequest createObraSocialRequest);

    /**
     * Actualiza una obra social existente con datos de {@code UpdateObraSocialRequest}.
     * Un campo en {@code null} deja ese dato sin tocar.
     *
     * @param obraSocial {@code ObraSocial} entidad a actualizar
     * @param updateObraSocialRequest {@code UpdateObraSocialRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "updateObraSocialRequest.codigo")
    @Mapping(target = "nombre", source = "updateObraSocialRequest.nombre")
    @Mapping(target = "razonSocial", source = "updateObraSocialRequest.razonSocial")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updateObraSocial(@MappingTarget ObraSocial obraSocial, UpdateObraSocialRequest updateObraSocialRequest);

    /**
     * Convierte una entidad {@code ObraSocial} y sus planes a {@code CreateObraSocialResponse}.
     *
     * @param obraSocial {@code ObraSocial} entidad
     * @param planes {@code List<GetPlanAnidadoResponse>} planes ya mapeados
     * @return {@code CreateObraSocialResponse} respuesta de creación
     */
    @Mapping(target = "id", source = "obraSocial.id")
    @Mapping(target = "codigo", source = "obraSocial.codigo")
    @Mapping(target = "nombre", source = "obraSocial.nombre")
    @Mapping(target = "razonSocial", source = "obraSocial.razonSocial")
    @Mapping(target = "planes", source = "planes")
    CreateObraSocialResponse toCreateResponse(ObraSocial obraSocial, List<GetPlanAnidadoResponse> planes);

    /**
     * Convierte una entidad {@code ObraSocial} y sus planes a {@code GetObraSocialResponse}.
     *
     * @param obraSocial {@code ObraSocial} entidad
     * @param planes {@code List<GetPlanAnidadoResponse>} planes ya mapeados
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetObraSocialResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "obraSocial.id")
    @Mapping(target = "codigo", source = "obraSocial.codigo")
    @Mapping(target = "nombre", source = "obraSocial.nombre")
    @Mapping(target = "razonSocial", source = "obraSocial.razonSocial")
    @Mapping(target = "planes", source = "planes")
    @Mapping(target = "auditoria", source = "auditoria")
    GetObraSocialResponse toGetResponse(ObraSocial obraSocial, List<GetPlanAnidadoResponse> planes, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code ObraSocial} a {@code ListObraSocialResponse}.
     *
     * @param obraSocial {@code ObraSocial} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListObraSocialResponse} respuesta de listado
     */
    @Mapping(target = "auditoria", source = "auditoria")
    ListObraSocialResponse toListResponse(ObraSocial obraSocial, AuditoriaResponse auditoria);

    /**
     * Arma el {@code AuditoriaResponse} de una obra social. {@code ObraSocial} tiene soft delete
     * propio, así que se mapean todos los campos: {@code createdDate}, {@code createdBy},
     * {@code lastModifiedDate}, {@code lastModifiedBy}, {@code deletedAt}, {@code deletedBy},
     * {@code deletedReason}.
     *
     * @param obraSocial {@code ObraSocial} entidad
     * @return {@code AuditoriaResponse} datos de auditoría de la obra social
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(ObraSocial obraSocial);

    /**
     * Convierte una entidad {@code ObraSocial} a {@code SoftDeleteObraSocialResponse}.
     *
     * @param obraSocial {@code ObraSocial} entidad dada de baja
     * @return {@code SoftDeleteObraSocialResponse} respuesta de baja lógica
     */
    SoftDeleteObraSocialResponse toSoftDeleteResponse(ObraSocial obraSocial);

}
