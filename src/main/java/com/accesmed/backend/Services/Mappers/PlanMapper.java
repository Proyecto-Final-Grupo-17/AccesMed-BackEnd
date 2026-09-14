package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.ObraSocial.Request.CreatePlanAnidadoRequest;
import com.accesmed.backend.Records.ObraSocial.Response.GetPlanAnidadoResponse;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetCoberturaAnidadaResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper para la entidad {@code Plan}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: la FK {@code obraSocial} no puede resolverse con datos propios del request de plan
 * anidado. El App la busca/guarda primero y se la pasa al mapper como parámetro
 * {@code @Context}, para que el mapeo de la lista completa (entidad y FK) quede en el
 * mapper y el App no itere manualmente. El estado ya no se persiste en la entidad: en los
 * responses, el campo {@code estadoActual} se alimenta desde el parámetro
 * {@code estadoVigente} que el App calcula del histórico y pasa al mapper.
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
     * @param obraSocial {@code ObraSocial} obra social ya guardada a la que pertenece el plan
     * @return {@code Plan} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "createPlanAnidadoRequest.codigo")
    @Mapping(target = "nombre", source = "createPlanAnidadoRequest.nombre")
    @Mapping(target = "obraSocial", expression = "java(obraSocial)")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    Plan toEntity(CreatePlanAnidadoRequest createPlanAnidadoRequest, @Context ObraSocial obraSocial);

    /**
     * Convierte una lista de {@code CreatePlanAnidadoRequest} a una lista de entidades
     * {@code Plan}, reutilizando el mapeo singular para cada elemento (incluida la
     * resolución de la FK {@code obraSocial} vía contexto). Utilizado durante la creación
     * anidada de planes en una obra social.
     *
     * @param createPlanesAnidadoRequest {@code List<CreatePlanAnidadoRequest>} datos de los planes anidados
     * @param obraSocial {@code ObraSocial} obra social ya guardada a la que pertenecen los planes
     * @return {@code List<Plan>} entidades listas para persistir
     */
    List<Plan> toEntities(List<CreatePlanAnidadoRequest> createPlanesAnidadoRequest, @Context ObraSocial obraSocial);

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
     * @param coberturas {@code List<GetCoberturaAnidadaResponse>} coberturas ya mapeadas del plan
     * @return {@code GetPlanResponse} respuesta de obtención (también usada al agregar y actualizar)
     */
    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "codigo", source = "plan.codigo")
    @Mapping(target = "nombre", source = "plan.nombre")
    @Mapping(target = "obraSocialId", source = "plan.obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "plan.obraSocial.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    @Mapping(target = "coberturas", source = "coberturas")
    GetPlanResponse toGetResponse(Plan plan, EstadoPlan estadoVigente, List<GetCoberturaAnidadaResponse> coberturas);

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
     * @param coberturas {@code List<GetCoberturaAnidadaResponse>} coberturas ya mapeadas del plan
     * @return {@code GetPlanAnidadoResponse} respuesta anidada
     */
    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "codigo", source = "plan.codigo")
    @Mapping(target = "nombre", source = "plan.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    @Mapping(target = "coberturas", source = "coberturas")
    GetPlanAnidadoResponse toGetPlanAnidadoResponse(Plan plan, EstadoPlan estadoVigente, List<GetCoberturaAnidadaResponse> coberturas);

}
