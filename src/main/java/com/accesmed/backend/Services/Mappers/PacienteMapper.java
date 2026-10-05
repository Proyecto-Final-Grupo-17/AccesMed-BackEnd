package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.Paciente.Request.CreatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Request.UpdatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Response.CreatePacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.GetObraSocialAnidadaResponse;
import com.accesmed.backend.Records.Paciente.Response.GetPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.ListPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.SoftDeletePacienteResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper para la entidad {@code Paciente}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: las coberturas de obra social anidadas se arman en el {@code PacienteApp} (vía
 * {@code ObraSocialPacienteMapper}) y se reciben ya mapeadas en los métodos de respuesta.
 */
@Mapper(componentModel = "spring")
public interface PacienteMapper {

    /**
     * Convierte un {@code CreatePacienteRequest} a una entidad {@code Paciente}.
     *
     * @param createPacienteRequest {@code CreatePacienteRequest} datos del request
     * @return {@code Paciente} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    Paciente toEntity(CreatePacienteRequest createPacienteRequest);

    /**
     * Actualiza un paciente existente con datos de {@code UpdatePacienteRequest}. Un campo
     * en {@code null} deja ese dato sin tocar.
     *
     * @param paciente {@code Paciente} entidad a actualizar
     * @param updatePacienteRequest {@code UpdatePacienteRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updatePaciente(@MappingTarget Paciente paciente, UpdatePacienteRequest updatePacienteRequest);

    /**
     * Convierte una entidad {@code Paciente} y sus coberturas a {@code CreatePacienteResponse}.
     *
     * @param paciente {@code Paciente} entidad
     * @param obrasSociales {@code List<GetObraSocialAnidadaResponse>} coberturas ya mapeadas
     * @return {@code CreatePacienteResponse} respuesta de creación
     */
    CreatePacienteResponse toCreateResponse(Paciente paciente, List<GetObraSocialAnidadaResponse> obrasSociales);

    /**
     * Convierte una entidad {@code Paciente} y sus coberturas a {@code GetPacienteResponse}.
     *
     * @param paciente {@code Paciente} entidad
     * @param obrasSociales {@code List<GetObraSocialAnidadaResponse>} coberturas ya mapeadas
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetPacienteResponse} respuesta de obtención
     */
    @Mapping(target = "auditoria", source = "auditoria")
    GetPacienteResponse toGetResponse(Paciente paciente, List<GetObraSocialAnidadaResponse> obrasSociales, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code Paciente} a {@code ListPacienteResponse}.
     *
     * @param paciente {@code Paciente} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListPacienteResponse} respuesta de listado
     */
    @Mapping(target = "auditoria", source = "auditoria")
    ListPacienteResponse toListResponse(Paciente paciente, AuditoriaResponse auditoria);

    /**
     * Arma el {@code AuditoriaResponse} de un paciente. {@code Paciente} tiene soft delete
     * propio, así que se mapean todos los campos: {@code createdDate}, {@code createdBy},
     * {@code lastModifiedDate}, {@code lastModifiedBy}, {@code deletedAt}, {@code deletedBy},
     * {@code deletedReason}.
     *
     * @param paciente {@code Paciente} entidad
     * @return {@code AuditoriaResponse} datos de auditoría del paciente
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(Paciente paciente);

    /**
     * Convierte una entidad {@code Paciente} a {@code SoftDeletePacienteResponse}.
     *
     * @param paciente {@code Paciente} entidad dada de baja
     * @return {@code SoftDeletePacienteResponse} respuesta de baja lógica
     */
    SoftDeletePacienteResponse toSoftDeleteResponse(Paciente paciente);

}
