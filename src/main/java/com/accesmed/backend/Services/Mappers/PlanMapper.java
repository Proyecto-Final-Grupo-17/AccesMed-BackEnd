package com.accesmed.backend.Services.Mappers;

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

import java.util.List;

/**
 * Mapper para la entidad {@code Plan}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: la FK {@code obraSocial} se ignora en el mapeo y se setea en el App después de
 * validar su existencia; {@code estadoActual} lo setea el {@code DomainService} al abrir
 * el tramo inicial.
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
    @Mapping(target = "estadoActual", ignore = true)
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
    @Mapping(target = "estadoActual", ignore = true)
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
    @Mapping(target = "estadoActual", ignore = true)
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
     * @return {@code GetPlanResponse} respuesta de obtención (también usada al agregar y actualizar)
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "obraSocialId", source = "obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "obraSocial.nombre")
    @Mapping(target = "estadoActual", source = "estadoActual")
    GetPlanResponse toGetResponse(Plan plan);

    /**
     * Convierte una entidad {@code Plan} a {@code ListPlanResponse}.
     *
     * @param plan {@code Plan} entidad
     * @return {@code ListPlanResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "obraSocialId", source = "obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "obraSocial.nombre")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ListPlanResponse toListResponse(Plan plan);

    /**
     * Convierte una lista de entidades {@code Plan} a una lista de {@code ListPlanResponse}.
     *
     * @param planes {@code List<Plan>} lista de entidades
     * @return {@code List<ListPlanResponse>} lista de respuestas
     */
    List<ListPlanResponse> toListResponses(List<Plan> planes);

    /**
     * Convierte una entidad {@code Plan} a {@code CambioEstadoPlanResponse}.
     *
     * @param plan {@code Plan} entidad tras la transición de estado
     * @return {@code CambioEstadoPlanResponse} respuesta de la transición
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "estadoActual", source = "estadoActual")
    CambioEstadoPlanResponse toCambioEstadoResponse(Plan plan);

    /**
     * Convierte una entidad {@code Plan} a {@code GetPlanAnidadoResponse}, para anidar en
     * las respuestas de {@code ObraSocial}.
     *
     * @param plan {@code Plan} entidad
     * @return {@code GetPlanAnidadoResponse} respuesta anidada
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "estadoActual", source = "estadoActual")
    GetPlanAnidadoResponse toGetPlanAnidadoResponse(Plan plan);

    /**
     * Convierte una lista de entidades {@code Plan} a una lista de {@code GetPlanAnidadoResponse}.
     *
     * @param planes {@code List<Plan>} lista de entidades
     * @return {@code List<GetPlanAnidadoResponse>} lista de respuestas anidadas
     */
    List<GetPlanAnidadoResponse> toGetPlanAnidadoResponses(List<Plan> planes);

}
