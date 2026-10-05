package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Records.Medico.Request.AsignarPrestacionAnidadaRequest;
import com.accesmed.backend.Records.Medico.Response.GetPrestacionAnidadaResponse;
import com.accesmed.backend.Records.MedicoPrestacion.Request.AssignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Response.GetMedicoPrestacionResponse;
import com.accesmed.backend.Records.MedicoPrestacion.Response.UnassignMedicoPrestacionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper para la entidad {@code MedicoPrestacion}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: {@code medico} y {@code prestacion} se setean en el {@code App} tras buscar las
 * entidades activas correspondientes, no en este mapper.
 */
@Mapper(componentModel = "spring")
public interface MedicoPrestacionMapper {

    /**
     * Convierte un {@code AssignMedicoPrestacionRequest} a una entidad {@code MedicoPrestacion}.
     *
     * @param assignMedicoPrestacionRequest {@code AssignMedicoPrestacionRequest} datos del request
     * @return {@code MedicoPrestacion} entidad lista para persistir (sin médico ni prestación)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medico", ignore = true)
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "fechaInicioVigencia", ignore = true)
    @Mapping(target = "fechaFinVigencia", ignore = true)
    MedicoPrestacion toEntity(AssignMedicoPrestacionRequest assignMedicoPrestacionRequest);

    /**
     * Convierte un {@code AsignarPrestacionAnidadaRequest} a una entidad {@code MedicoPrestacion}.
     *
     * @param asignarPrestacionAnidadaRequest {@code AsignarPrestacionAnidadaRequest} datos del request
     * @return {@code MedicoPrestacion} entidad lista para persistir (sin médico ni prestación)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medico", ignore = true)
    @Mapping(target = "prestacion", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "fechaInicioVigencia", ignore = true)
    @Mapping(target = "fechaFinVigencia", ignore = true)
    MedicoPrestacion toEntity(AsignarPrestacionAnidadaRequest asignarPrestacionAnidadaRequest);

    /**
     * Convierte una entidad {@code MedicoPrestacion} a {@code GetMedicoPrestacionResponse}.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} entidad
     * @return {@code GetMedicoPrestacionResponse} respuesta de asignación
     */
    @Mapping(target = "medicoId", source = "medico.id")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionCodigo", source = "prestacion.codigo")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    GetMedicoPrestacionResponse toGetResponse(MedicoPrestacion medicoPrestacion);

    /**
     * Convierte una entidad {@code MedicoPrestacion} a {@code GetPrestacionAnidadaResponse},
     * para colgar de las respuestas de médico.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} entidad
     * @return {@code GetPrestacionAnidadaResponse} respuesta de prestación anidada
     */
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionCodigo", source = "prestacion.codigo")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    GetPrestacionAnidadaResponse toGetPrestacionAnidadaResponse(MedicoPrestacion medicoPrestacion);

    /**
     * Convierte una lista de entidades {@code MedicoPrestacion} a una lista de
     * {@code GetPrestacionAnidadaResponse}.
     *
     * @param medicoPrestaciones {@code List<MedicoPrestacion>} lista de entidades
     * @return {@code List<GetPrestacionAnidadaResponse>} lista de respuestas
     */
    List<GetPrestacionAnidadaResponse> toGetPrestacionAnidadaResponses(List<MedicoPrestacion> medicoPrestaciones);

    /**
     * Convierte una entidad {@code MedicoPrestacion} a {@code UnassignMedicoPrestacionResponse}.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} entidad con la vigencia cerrada
     * @return {@code UnassignMedicoPrestacionResponse} respuesta de cierre de vigencia
     */
    UnassignMedicoPrestacionResponse toUnassignResponse(MedicoPrestacion medicoPrestacion);

}
