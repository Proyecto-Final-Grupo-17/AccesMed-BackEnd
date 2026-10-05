package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
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
     * Convierte una entidad {@code IndicacionPrestacionTurno} y sus datos de auditoría a
     * {@code GetIndicacionPrestacionTurnoResponse}.
     *
     * @param indicacionPrestacionTurno {@code IndicacionPrestacionTurno} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetIndicacionPrestacionTurnoResponse} respuesta de detalle
     */
    @Mapping(target = "id", source = "indicacionPrestacionTurno.id")
    @Mapping(target = "turnoId", source = "indicacionPrestacionTurno.turno.id")
    @Mapping(target = "indicacionPrestacionId", source = "indicacionPrestacionTurno.indicacionPrestacion.id")
    @Mapping(target = "indicacionPrestacionNombre", source = "indicacionPrestacionTurno.indicacionPrestacion.nombre")
    @Mapping(target = "indicacionPrestacionDescripcion", source = "indicacionPrestacionTurno.indicacionPrestacion.descripcion")
    @Mapping(target = "requiereValidacion", source = "indicacionPrestacionTurno.indicacionPrestacion.requiereValidacion")
    @Mapping(target = "fechaHoraValidacion", source = "indicacionPrestacionTurno.fechaHoraValidacion")
    @Mapping(target = "validadoPorId", source = "indicacionPrestacionTurno.validadoPor.id")
    @Mapping(target = "auditoria", source = "auditoria")
    GetIndicacionPrestacionTurnoResponse toGetResponse(IndicacionPrestacionTurno indicacionPrestacionTurno, AuditoriaResponse auditoria);

    /**
     * Convierte una entidad {@code IndicacionPrestacionTurno} a
     * {@code ListIndicacionPrestacionTurnoResponse}.
     *
     * @param indicacionPrestacionTurno {@code IndicacionPrestacionTurno} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListIndicacionPrestacionTurnoResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "indicacionPrestacionTurno.id")
    @Mapping(target = "turnoId", source = "indicacionPrestacionTurno.turno.id")
    @Mapping(target = "indicacionPrestacionId", source = "indicacionPrestacionTurno.indicacionPrestacion.id")
    @Mapping(target = "indicacionPrestacionNombre", source = "indicacionPrestacionTurno.indicacionPrestacion.nombre")
    @Mapping(target = "indicacionPrestacionDescripcion", source = "indicacionPrestacionTurno.indicacionPrestacion.descripcion")
    @Mapping(target = "requiereValidacion", source = "indicacionPrestacionTurno.indicacionPrestacion.requiereValidacion")
    @Mapping(target = "fechaHoraValidacion", source = "indicacionPrestacionTurno.fechaHoraValidacion")
    @Mapping(target = "validadoPorId", source = "indicacionPrestacionTurno.validadoPor.id")
    @Mapping(target = "auditoria", source = "auditoria")
    ListIndicacionPrestacionTurnoResponse toListResponse(IndicacionPrestacionTurno indicacionPrestacionTurno, AuditoriaResponse auditoria);

    /**
     * Arma el {@code AuditoriaResponse} de una indicación de prestación de turno.
     * {@code IndicacionPrestacionTurno} sí tiene soft delete propio, así que
     * {@code deletedAt}/{@code deletedBy}/{@code deletedReason} se mapean normalmente.
     *
     * @param indicacionPrestacionTurno {@code IndicacionPrestacionTurno} entidad
     * @return {@code AuditoriaResponse} datos de auditoría de la indicación de turno
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(IndicacionPrestacionTurno indicacionPrestacionTurno);

}
