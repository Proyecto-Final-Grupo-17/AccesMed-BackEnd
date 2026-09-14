package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Records.ObraSocialPrestacion.Request.AssignObraSocialPrestacionRequest;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.GetObraSocialPrestacionResponse;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.ListObraSocialPrestacionResponse;
import com.accesmed.backend.Records.ObraSocialPrestacion.Request.UpdateObraSocialPrestacionRequest;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.UnassignObraSocialPrestacionResponse;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.UpdateObraSocialPrestacionResponse;
import com.accesmed.backend.Records.Plan.Request.AsignarCoberturaAnidadaRequest;
import com.accesmed.backend.Records.Plan.Response.GetCoberturaAnidadaResponse;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper para la entidad {@code ObraSocialPlanPrestacion}. Realiza conversiones entre
 * records de request/response y la entidad JPA.
 *
 * Nota: {@code plan} y {@code prestacion} se setean en el {@code App} tras buscar las
 * entidades activas correspondientes, no en este mapper.
 */
@Mapper(componentModel = "spring")
public interface ObraSocialPlanPrestacionMapper {

    /**
     * Convierte un {@code AssignObraSocialPrestacionRequest} a una entidad
     * {@code ObraSocialPlanPrestacion}.
     *
     * @param assignObraSocialPrestacionRequest {@code AssignObraSocialPrestacionRequest} datos del request
     * @return {@code ObraSocialPlanPrestacion} entidad lista para persistir (sin plan ni prestación)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    ObraSocialPlanPrestacion toEntity(AssignObraSocialPrestacionRequest assignObraSocialPrestacionRequest);

    /**
     * Convierte un {@code AsignarCoberturaAnidadaRequest} a una entidad
     * {@code ObraSocialPlanPrestacion}.
     *
     * @param asignarCoberturaAnidadaRequest {@code AsignarCoberturaAnidadaRequest} datos del request
     * @return {@code ObraSocialPlanPrestacion} entidad lista para persistir (sin plan ni prestación)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    ObraSocialPlanPrestacion toEntity(AsignarCoberturaAnidadaRequest asignarCoberturaAnidadaRequest);

    /**
     * Convierte una entidad {@code ObraSocialPlanPrestacion} a {@code GetObraSocialPrestacionResponse}.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad
     * @return {@code GetObraSocialPrestacionResponse} respuesta de asignación
     */
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planCodigo", source = "plan.codigo")
    @Mapping(target = "planNombre", source = "plan.nombre")
    @Mapping(target = "obraSocialId", source = "plan.obraSocial.id")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionCodigo", source = "prestacion.codigo")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    GetObraSocialPrestacionResponse toGetResponse(ObraSocialPlanPrestacion obraSocialPlanPrestacion);

    /**
     * Convierte una entidad {@code ObraSocialPlanPrestacion} a {@code ListObraSocialPrestacionResponse}.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *        consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListObraSocialPrestacionResponse} respuesta de listado
     */
    @Mapping(target = "planId", source = "obraSocialPlanPrestacion.plan.id")
    @Mapping(target = "planCodigo", source = "obraSocialPlanPrestacion.plan.codigo")
    @Mapping(target = "planNombre", source = "obraSocialPlanPrestacion.plan.nombre")
    @Mapping(target = "obraSocialId", source = "obraSocialPlanPrestacion.plan.obraSocial.id")
    @Mapping(target = "prestacionId", source = "obraSocialPlanPrestacion.prestacion.id")
    @Mapping(target = "prestacionCodigo", source = "obraSocialPlanPrestacion.prestacion.codigo")
    @Mapping(target = "prestacionNombre", source = "obraSocialPlanPrestacion.prestacion.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    ListObraSocialPrestacionResponse toListResponse(ObraSocialPlanPrestacion obraSocialPlanPrestacion, AuditoriaResponse auditoria);

    /**
     * Aplica los datos de un {@code UpdateObraSocialPrestacionRequest} sobre una cobertura
     * existente, pisando modalidad, porcentaje y coseguro.
     *
     * @param updateObraSocialPrestacionRequest {@code UpdateObraSocialPrestacionRequest} datos nuevos de la cobertura
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} cobertura a actualizar (se modifica in place)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "prestacion", ignore = true)
    void updateEntityFromRequest(UpdateObraSocialPrestacionRequest updateObraSocialPrestacionRequest,
            @MappingTarget ObraSocialPlanPrestacion obraSocialPlanPrestacion);

    /**
     * Convierte una entidad {@code ObraSocialPlanPrestacion} a {@code UpdateObraSocialPrestacionResponse}.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad actualizada
     * @return {@code UpdateObraSocialPrestacionResponse} respuesta de actualización
     */
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    UpdateObraSocialPrestacionResponse toUpdateResponse(ObraSocialPlanPrestacion obraSocialPlanPrestacion);

    /**
     * Convierte una entidad {@code ObraSocialPlanPrestacion} a {@code UnassignObraSocialPrestacionResponse}.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad dada de baja
     * @return {@code UnassignObraSocialPrestacionResponse} respuesta de baja lógica
     */
    UnassignObraSocialPrestacionResponse toUnassignResponse(ObraSocialPlanPrestacion obraSocialPlanPrestacion);

    /**
     * Convierte una entidad {@code ObraSocialPlanPrestacion} a {@code GetCoberturaAnidadaResponse},
     * para colgar de las respuestas de plan.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad
     * @return {@code GetCoberturaAnidadaResponse} respuesta de cobertura anidada
     */
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionCodigo", source = "prestacion.codigo")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    GetCoberturaAnidadaResponse toGetCoberturaAnidadaResponse(ObraSocialPlanPrestacion obraSocialPlanPrestacion);

    /**
     * Convierte una lista de entidades {@code ObraSocialPlanPrestacion} a una lista de
     * {@code GetCoberturaAnidadaResponse}.
     *
     * @param coberturas {@code List<ObraSocialPlanPrestacion>} lista de entidades
     * @return {@code List<GetCoberturaAnidadaResponse>} lista de respuestas
     */
    List<GetCoberturaAnidadaResponse> toGetCoberturaAnidadaResponses(List<ObraSocialPlanPrestacion> coberturas);

    /**
     * Arma el {@code AuditoriaResponse} de una cobertura plan-prestación. {@code ObraSocialPlanPrestacion}
     * tiene soft delete propio, así que {@code deletedAt}/{@code deletedBy}/{@code deletedReason} se
     * mapean normalmente.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad
     * @return {@code AuditoriaResponse} datos de auditoría de la cobertura
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(ObraSocialPlanPrestacion obraSocialPlanPrestacion);

}
