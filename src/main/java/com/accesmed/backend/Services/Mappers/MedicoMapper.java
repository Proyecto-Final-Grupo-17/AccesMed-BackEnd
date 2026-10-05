package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.Medico.Request.CreateMedicoRequest;
import com.accesmed.backend.Records.Medico.Request.UpdateMedicoRequest;
import com.accesmed.backend.Records.Medico.Response.CreateMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.GetMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.GetPrestacionAnidadaResponse;
import com.accesmed.backend.Records.Medico.Response.ListMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.SoftDeleteMedicoResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper para la entidad {@code Medico}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: la especialidad se setea en el {@code MedicoApp} tras buscarla activa, no en
 * este mapper. Las prestaciones anidadas se arman en el {@code MedicoApp} (vía
 * {@code MedicoPrestacionMapper}) y se reciben ya mapeadas en los métodos de respuesta.
 */
@Mapper(componentModel = "spring")
public interface MedicoMapper {

    /**
     * Convierte un {@code CreateMedicoRequest} a una entidad {@code Medico}.
     *
     * @param createMedicoRequest {@code CreateMedicoRequest} datos del request
     * @return {@code Medico} entidad lista para persistir (sin especialidad)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    Medico toEntity(CreateMedicoRequest createMedicoRequest);

    /**
     * Actualiza un médico existente con datos de {@code UpdateMedicoRequest}. Un campo en
     * {@code null} deja ese dato sin tocar. La especialidad se setea en el {@code MedicoApp}
     * si vino {@code especialidadId}.
     *
     * @param medico {@code Medico} entidad a actualizar
     * @param updateMedicoRequest {@code UpdateMedicoRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updateMedico(@MappingTarget Medico medico, UpdateMedicoRequest updateMedicoRequest);

    /**
     * Convierte una entidad {@code Medico} y sus prestaciones a {@code CreateMedicoResponse}.
     *
     * @param medico {@code Medico} entidad
     * @param prestaciones {@code List<GetPrestacionAnidadaResponse>} prestaciones ya mapeadas
     * @return {@code CreateMedicoResponse} respuesta de creación
     */
    @Mapping(target = "especialidadId", source = "medico.especialidad.id")
    @Mapping(target = "prestaciones", source = "prestaciones")
    CreateMedicoResponse toCreateResponse(Medico medico, List<GetPrestacionAnidadaResponse> prestaciones);

    /**
     * Convierte una entidad {@code Medico} y sus prestaciones a {@code GetMedicoResponse}.
     *
     * @param medico {@code Medico} entidad
     * @param prestaciones {@code List<GetPrestacionAnidadaResponse>} prestaciones ya mapeadas
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetMedicoResponse} respuesta de obtención
     */
    @Mapping(target = "especialidadId", source = "medico.especialidad.id")
    @Mapping(target = "prestaciones", source = "prestaciones")
    @Mapping(target = "auditoria", source = "auditoria")
    GetMedicoResponse toGetResponse(Medico medico, List<GetPrestacionAnidadaResponse> prestaciones, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code Medico} a {@code ListMedicoResponse}.
     *
     * @param medico {@code Medico} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListMedicoResponse} respuesta de listado
     */
    @Mapping(target = "especialidadId", source = "medico.especialidad.id")
    @Mapping(target = "auditoria", source = "auditoria")
    ListMedicoResponse toListResponse(Medico medico, AuditoriaResponse auditoria);

    /**
     * Arma el {@code AuditoriaResponse} de un médico. {@code Medico} tiene soft delete propio,
     * así que se mapean todos los campos: {@code createdDate}, {@code createdBy},
     * {@code lastModifiedDate}, {@code lastModifiedBy}, {@code deletedAt}, {@code deletedBy},
     * {@code deletedReason}.
     *
     * @param medico {@code Medico} entidad
     * @return {@code AuditoriaResponse} datos de auditoría del médico
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(Medico medico);

    /**
     * Convierte una entidad {@code Medico} a {@code SoftDeleteMedicoResponse}.
     *
     * @param medico {@code Medico} entidad dada de baja
     * @return {@code SoftDeleteMedicoResponse} respuesta de baja lógica
     */
    SoftDeleteMedicoResponse toSoftDeleteResponse(Medico medico);

}
