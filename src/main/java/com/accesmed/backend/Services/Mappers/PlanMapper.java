package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.ObraSocial.Request.CreatePlanAnidadoRequest;
import com.accesmed.backend.Records.ObraSocial.Response.GetPlanAnidadoResponse;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper para la entidad {@code Plan}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: la FK {@code obraSocial} se ignora en el mapeo y se setea en el App después de
 * validar su existencia. El estado ya no se persiste en la entidad: en los responses, el
 * campo {@code estadoActual} se alimenta desde el parámetro {@code estadoVigente} que el
 * App calcula del histórico y pasa al mapper.
 */
@Mapper(componentModel = "spring")
public interface PlanMapper {

    /**
     * Convierte un {@code AddPlanRequest} a una entidad {@code Plan}.
     *
     * @param addPlanRequest {@code AddPlanRequest} datos del request
     * @return {@code Plan} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "obraSocial", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    Plan toEntity(AddPlanRequest addPlanRequest);

    /**
     * Convierte un {@code CreatePlanAnidadoRequest} (plan anidado en el alta de obra
     * social) a una entidad {@code Plan}.
     *
     * @param createPlanAnidadoRequest {@code CreatePlanAnidadoRequest} datos del plan anidado
     * @return {@code Plan} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "obraSocial", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    Plan toEntity(CreatePlanAnidadoRequest createPlanAnidadoRequest);

    /**
     * Actualiza código y nombre de un plan existente con datos de
     * {@code UpdatePlanRequest}. Un campo en {@code null} deja ese dato sin tocar.
     *
     * @param plan {@code Plan} entidad a actualizar
     * @param updatePlanRequest {@code UpdatePlanRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "updatePlanRequest.codigo")
    @Mapping(target = "nombre", source = "updatePlanRequest.nombre")
    @Mapping(target = "obraSocial", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updatePlan(@MappingTarget Plan plan, UpdatePlanRequest updatePlanRequest);

    /**
     * Convierte una entidad {@code Plan} a {@code GetPlanResponse}.
     *
     * @param plan {@code Plan} entidad
     * @param estadoVigente {@code EstadoPlan} estado vigente calculado del histórico
     * @return {@code GetPlanResponse} respuesta de obtención (también usada al agregar y actualizar)
     */
    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "codigo", source = "plan.codigo")
    @Mapping(target = "nombre", source = "plan.nombre")
    @Mapping(target = "obraSocialId", source = "plan.obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "plan.obraSocial.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    GetPlanResponse toGetResponse(Plan plan, EstadoPlan estadoVigente);

    /**
     * Convierte una entidad {@code Plan} a {@code ListPlanResponse}.
     *
     * @param plan {@code Plan} entidad
     * @param estadoVigente {@code EstadoPlan} estado vigente calculado del histórico
     * @return {@code ListPlanResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "codigo", source = "plan.codigo")
    @Mapping(target = "nombre", source = "plan.nombre")
    @Mapping(target = "obraSocialId", source = "plan.obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "plan.obraSocial.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    ListPlanResponse toListResponse(Plan plan, EstadoPlan estadoVigente);

    /**
     * Convierte una entidad {@code Plan} a {@code CambioEstadoPlanResponse}.
     *
     * @param plan {@code Plan} entidad tras la transición de estado
     * @param estadoVigente {@code EstadoPlan} estado vigente tras la transición
     * @return {@code CambioEstadoPlanResponse} respuesta de la transición
     */
    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "codigo", source = "plan.codigo")
    @Mapping(target = "nombre", source = "plan.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    CambioEstadoPlanResponse toCambioEstadoResponse(Plan plan, EstadoPlan estadoVigente);

    /**
     * Convierte una entidad {@code Plan} a {@code GetPlanAnidadoResponse}, para anidar en
     * las respuestas de {@code ObraSocial}.
     *
     * @param plan {@code Plan} entidad
     * @param estadoVigente {@code EstadoPlan} estado vigente calculado del histórico
     * @return {@code GetPlanAnidadoResponse} respuesta anidada
     */
    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "codigo", source = "plan.codigo")
    @Mapping(target = "nombre", source = "plan.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    GetPlanAnidadoResponse toGetPlanAnidadoResponse(Plan plan, EstadoPlan estadoVigente);

}
