package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.ObraSocial;
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
     * @return {@code GetObraSocialResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "obraSocial.id")
    @Mapping(target = "codigo", source = "obraSocial.codigo")
    @Mapping(target = "nombre", source = "obraSocial.nombre")
    @Mapping(target = "razonSocial", source = "obraSocial.razonSocial")
    @Mapping(target = "planes", source = "planes")
    GetObraSocialResponse toGetResponse(ObraSocial obraSocial, List<GetPlanAnidadoResponse> planes);

    /**
     * Convierte una entidad {@code ObraSocial} a {@code ListObraSocialResponse}.
     *
     * @param obraSocial {@code ObraSocial} entidad
     * @return {@code ListObraSocialResponse} respuesta de listado
     */
    ListObraSocialResponse toListResponse(ObraSocial obraSocial);

    /**
     * Convierte una lista de entidades {@code ObraSocial} a una lista de {@code ListObraSocialResponse}.
     *
     * @param obrasSociales {@code List<ObraSocial>} lista de entidades
     * @return {@code List<ListObraSocialResponse>} lista de respuestas
     */
    List<ListObraSocialResponse> toListResponses(List<ObraSocial> obrasSociales);

    /**
     * Convierte una entidad {@code ObraSocial} a {@code SoftDeleteObraSocialResponse}.
     *
     * @param obraSocial {@code ObraSocial} entidad dada de baja
     * @return {@code SoftDeleteObraSocialResponse} respuesta de baja lógica
     */
    SoftDeleteObraSocialResponse toSoftDeleteResponse(ObraSocial obraSocial);

}
