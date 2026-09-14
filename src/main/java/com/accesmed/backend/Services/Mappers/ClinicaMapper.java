package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Records.Clinica.Request.UpdateClinicaRequest;
import com.accesmed.backend.Records.Clinica.Response.GetClinicaResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper para la entidad {@code Clinica}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 */
@Mapper(componentModel = "spring")
public interface ClinicaMapper {

    /**
     * Actualiza la clínica existente con los datos de {@code UpdateClinicaRequest}. Un
     * campo en {@code null} deja ese dato sin tocar.
     *
     * @param clinica {@code Clinica} entidad a actualizar
     * @param updateClinicaRequest {@code UpdateClinicaRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updateClinica(@MappingTarget Clinica clinica, UpdateClinicaRequest updateClinicaRequest);

    /**
     * Convierte una entidad {@code Clinica} a {@code GetClinicaResponse}.
     *
     * @param clinica {@code Clinica} entidad
     * @return {@code GetClinicaResponse} respuesta de obtención
     */
    GetClinicaResponse toGetResponse(Clinica clinica);

}
