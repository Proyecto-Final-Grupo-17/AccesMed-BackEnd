package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Records.ObraSocialPaciente.Response.GetObraSocialPacienteResponse;
import com.accesmed.backend.Records.ObraSocialPaciente.Response.SoftDeleteObraSocialPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.GetObraSocialAnidadaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper para la entidad {@code ObraSocialPaciente}. Realiza conversiones entre entidad
 * JPA y records de response.
 *
 * Nota: la entidad es inmutable salvo baja (sin setters de {@code paciente}/{@code plan}/
 * {@code nroSocio}): el {@code ObraSocialPacienteApp} la construye directamente con su
 * constructor tras buscar el paciente y el plan activos, así que este mapper no tiene
 * método {@code toEntity}.
 */
@Mapper(componentModel = "spring")
public interface ObraSocialPacienteMapper {

    /**
     * Convierte una entidad {@code ObraSocialPaciente} a {@code GetObraSocialPacienteResponse}.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} entidad
     * @return {@code GetObraSocialPacienteResponse} respuesta de asignación
     */
    @Mapping(target = "pacienteId", source = "paciente.id")
    @Mapping(target = "obraSocialId", source = "plan.obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "plan.obraSocial.nombre")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planNombre", source = "plan.nombre")
    GetObraSocialPacienteResponse toGetResponse(ObraSocialPaciente obraSocialPaciente);

    /**
     * Convierte una entidad {@code ObraSocialPaciente} a {@code GetObraSocialAnidadaResponse},
     * para colgar de las respuestas de paciente.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} entidad
     * @return {@code GetObraSocialAnidadaResponse} respuesta de cobertura anidada
     */
    @Mapping(target = "obraSocialId", source = "plan.obraSocial.id")
    @Mapping(target = "obraSocialNombre", source = "plan.obraSocial.nombre")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planNombre", source = "plan.nombre")
    GetObraSocialAnidadaResponse toGetObraSocialAnidadaResponse(ObraSocialPaciente obraSocialPaciente);

    /**
     * Convierte una lista de entidades {@code ObraSocialPaciente} a una lista de
     * {@code GetObraSocialAnidadaResponse}.
     *
     * @param obrasSocialesPaciente {@code List<ObraSocialPaciente>} lista de entidades
     * @return {@code List<GetObraSocialAnidadaResponse>} lista de respuestas
     */
    List<GetObraSocialAnidadaResponse> toGetObraSocialAnidadaResponses(List<ObraSocialPaciente> obrasSocialesPaciente);

    /**
     * Convierte una entidad {@code ObraSocialPaciente} a {@code SoftDeleteObraSocialPacienteResponse}.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} entidad dada de baja
     * @return {@code SoftDeleteObraSocialPacienteResponse} respuesta de baja lógica
     */
    SoftDeleteObraSocialPacienteResponse toSoftDeleteResponse(ObraSocialPaciente obraSocialPaciente);

}
