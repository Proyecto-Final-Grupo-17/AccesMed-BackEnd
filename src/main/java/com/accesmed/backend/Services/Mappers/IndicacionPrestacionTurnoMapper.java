package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import com.accesmed.backend.Records.IndicacionPrestacionTurno.Response.GetIndicacionPrestacionTurnoResponse;
import com.accesmed.backend.Records.IndicacionPrestacionTurno.Response.ListIndicacionPrestacionTurnoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper para la entidad {@code IndicacionPrestacionTurno}. Realiza conversiones entre
 * records de response y la entidad JPA, con mapeos anidados hacia {@code IndicacionPrestacion}
 * y {@code Admin} vía relaciones.
 */
@Mapper(componentModel = "spring")
public interface IndicacionPrestacionTurnoMapper {

    /**
     * Convierte una entidad {@code IndicacionPrestacionTurno} a
     * {@code GetIndicacionPrestacionTurnoResponse}.
     *
     * @param indicacionPrestacionTurno {@code IndicacionPrestacionTurno} entidad
     * @return {@code GetIndicacionPrestacionTurnoResponse} respuesta de detalle
     */
    @Mapping(target = "turnoId", source = "turno.id")
    @Mapping(target = "indicacionPrestacionId", source = "indicacionPrestacion.id")
    @Mapping(target = "indicacionPrestacionNombre", source = "indicacionPrestacion.nombre")
    @Mapping(target = "indicacionPrestacionDescripcion", source = "indicacionPrestacion.descripcion")
    @Mapping(target = "requiereValidacion", source = "indicacionPrestacion.requiereValidacion")
    @Mapping(target = "validadoPorId", source = "validadoPor.id")
    GetIndicacionPrestacionTurnoResponse toGetResponse(IndicacionPrestacionTurno indicacionPrestacionTurno);

    /**
     * Convierte una entidad {@code IndicacionPrestacionTurno} a
     * {@code ListIndicacionPrestacionTurnoResponse}.
     *
     * @param indicacionPrestacionTurno {@code IndicacionPrestacionTurno} entidad
     * @return {@code ListIndicacionPrestacionTurnoResponse} respuesta de listado
     */
    @Mapping(target = "turnoId", source = "turno.id")
    @Mapping(target = "indicacionPrestacionId", source = "indicacionPrestacion.id")
    @Mapping(target = "indicacionPrestacionNombre", source = "indicacionPrestacion.nombre")
    @Mapping(target = "indicacionPrestacionDescripcion", source = "indicacionPrestacion.descripcion")
    @Mapping(target = "requiereValidacion", source = "indicacionPrestacion.requiereValidacion")
    @Mapping(target = "validadoPorId", source = "validadoPor.id")
    ListIndicacionPrestacionTurnoResponse toListResponse(IndicacionPrestacionTurno indicacionPrestacionTurno);

}
