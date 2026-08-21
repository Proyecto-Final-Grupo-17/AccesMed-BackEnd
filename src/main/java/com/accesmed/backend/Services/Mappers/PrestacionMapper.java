package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Request.CreatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Response.CambioEstadoPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.CreatePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.GetPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.Duration;
import java.util.List;

/**
 * Mapper para la entidad {@code Prestacion}. Realiza conversiones entre
 * records de request/response y la entidad JPA, con manejo de duraciones
 * en minutos ({@link Integer}) ↔ {@link Duration}.
 *
 * Nota: la FK {@code especialidad} se ignora en el mapeo y se setea en el App
 * después de validar su existencia. Del mismo modo, las indicaciones se arman en el App.
 * El estado ya no se persiste en la entidad: en los responses, el campo
 * {@code estadoActual} se alimenta desde el parámetro {@code estadoVigente} que el App
 * calcula del histórico y pasa al mapper.
 */
@Mapper(componentModel = "spring")
public interface PrestacionMapper {

    /**
     * Convierte un {@code CreatePrestacionRequest} a una entidad {@code Prestacion}.
     *
     * @param createPrestacionRequest {@code CreatePrestacionRequest} datos del request
     * @return {@code Prestacion} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "duracionMinima", source = "duracionMinimaMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "duracionMaxima", source = "duracionMaximaMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaSolicitud", source = "tiempoToleranciaSolicitudMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaValidacion", source = "tiempoToleranciaValidacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaReprogramacion", source = "tiempoToleranciaReprogramacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaConfirmacion", source = "tiempoToleranciaConfirmacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaCancelacion", source = "tiempoToleranciaCancelacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaAnuncio", source = "tiempoToleranciaAnuncioMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoRecordatorioConfirmacion", source = "tiempoRecordatorioConfirmacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    Prestacion toEntity(CreatePrestacionRequest createPrestacionRequest);

    /**
     * Actualiza nombre y tolerancias/duraciones de una prestación existente con datos de
     * {@code UpdatePrestacionRequest}. Un campo en {@code null} deja ese dato sin tocar.
     *
     * @param prestacion {@code Prestacion} entidad a actualizar
     * @param updatePrestacionRequest {@code UpdatePrestacionRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "nombre", source = "updatePrestacionRequest.nombre")
    @Mapping(target = "duracionMinima", source = "updatePrestacionRequest.duracionMinimaMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "duracionMaxima", source = "updatePrestacionRequest.duracionMaximaMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaSolicitud", source = "updatePrestacionRequest.tiempoToleranciaSolicitudMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaValidacion", source = "updatePrestacionRequest.tiempoToleranciaValidacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaReprogramacion", source = "updatePrestacionRequest.tiempoToleranciaReprogramacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaConfirmacion", source = "updatePrestacionRequest.tiempoToleranciaConfirmacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaCancelacion", source = "updatePrestacionRequest.tiempoToleranciaCancelacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaAnuncio", source = "updatePrestacionRequest.tiempoToleranciaAnuncioMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoRecordatorioConfirmacion", source = "updatePrestacionRequest.tiempoRecordatorioConfirmacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updatePrestacion(@MappingTarget Prestacion prestacion, UpdatePrestacionRequest updatePrestacionRequest);

    /**
     * Convierte una entidad {@code Prestacion} y su lista de indicaciones a {@code CreatePrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param indicaciones {@code List<GetIndicacionPrestacionResponse>} lista de indicaciones
     * @param estadoVigente {@code EstadoPrestacion} estado vigente calculado del histórico
     * @return {@code CreatePrestacionResponse} respuesta de creación
     */
    @Mapping(target = "id", source = "prestacion.id")
    @Mapping(target = "codigo", source = "prestacion.codigo")
    @Mapping(target = "nombre", source = "prestacion.nombre")
    @Mapping(target = "duracionMinimaMinutos", source = "prestacion.duracionMinima", qualifiedByName = "toMinutos")
    @Mapping(target = "duracionMaximaMinutos", source = "prestacion.duracionMaxima", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaSolicitudMinutos", source = "prestacion.tiempoToleranciaSolicitud", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaValidacionMinutos", source = "prestacion.tiempoToleranciaValidacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaReprogramacionMinutos", source = "prestacion.tiempoToleranciaReprogramacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaConfirmacionMinutos", source = "prestacion.tiempoToleranciaConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaCancelacionMinutos", source = "prestacion.tiempoToleranciaCancelacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaAnuncioMinutos", source = "prestacion.tiempoToleranciaAnuncio", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoRecordatorioConfirmacionMinutos", source = "prestacion.tiempoRecordatorioConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "especialidadId", source = "prestacion.especialidad.id")
    @Mapping(target = "especialidadNombre", source = "prestacion.especialidad.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    @Mapping(target = "indicaciones", source = "indicaciones")
    CreatePrestacionResponse toCreateResponse(Prestacion prestacion, List<GetIndicacionPrestacionResponse> indicaciones, EstadoPrestacion estadoVigente);

    /**
     * Convierte una entidad {@code Prestacion} a {@code UpdatePrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param estadoVigente {@code EstadoPrestacion} estado vigente calculado del histórico
     * @param cantidadHorariosRecalculados {@code int} horarios futuros libres recalculados por la cascada AGEN (A6)
     * @param cantidadHorariosDadosDeBaja {@code int} horarios futuros libres dados de baja por la cascada AGEN (A6)
     * @return {@code UpdatePrestacionResponse} respuesta de actualización
     */
    @Mapping(target = "id", source = "prestacion.id")
    @Mapping(target = "codigo", source = "prestacion.codigo")
    @Mapping(target = "nombre", source = "prestacion.nombre")
    @Mapping(target = "duracionMinimaMinutos", source = "prestacion.duracionMinima", qualifiedByName = "toMinutos")
    @Mapping(target = "duracionMaximaMinutos", source = "prestacion.duracionMaxima", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaSolicitudMinutos", source = "prestacion.tiempoToleranciaSolicitud", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaValidacionMinutos", source = "prestacion.tiempoToleranciaValidacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaReprogramacionMinutos", source = "prestacion.tiempoToleranciaReprogramacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaConfirmacionMinutos", source = "prestacion.tiempoToleranciaConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaCancelacionMinutos", source = "prestacion.tiempoToleranciaCancelacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaAnuncioMinutos", source = "prestacion.tiempoToleranciaAnuncio", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoRecordatorioConfirmacionMinutos", source = "prestacion.tiempoRecordatorioConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "especialidadId", source = "prestacion.especialidad.id")
    @Mapping(target = "especialidadNombre", source = "prestacion.especialidad.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    @Mapping(target = "cantidadHorariosRecalculados", source = "cantidadHorariosRecalculados")
    @Mapping(target = "cantidadHorariosDadosDeBaja", source = "cantidadHorariosDadosDeBaja")
    UpdatePrestacionResponse toUpdateResponse(Prestacion prestacion, EstadoPrestacion estadoVigente,
            int cantidadHorariosRecalculados, int cantidadHorariosDadosDeBaja);

    /**
     * Convierte una entidad {@code Prestacion} a {@code CambioEstadoPrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad tras la transición de estado
     * @param estadoVigente {@code EstadoPrestacion} estado vigente tras la transición
     * @return {@code CambioEstadoPrestacionResponse} respuesta de la transición
     */
    @Mapping(target = "id", source = "prestacion.id")
    @Mapping(target = "codigo", source = "prestacion.codigo")
    @Mapping(target = "nombre", source = "prestacion.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    CambioEstadoPrestacionResponse toCambioEstadoResponse(Prestacion prestacion, EstadoPrestacion estadoVigente);

    /**
     * Convierte una entidad {@code Prestacion} y su lista de indicaciones a {@code GetPrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param indicaciones {@code List<GetIndicacionPrestacionResponse>} lista de indicaciones
     * @param estadoVigente {@code EstadoPrestacion} estado vigente calculado del histórico
     * @return {@code GetPrestacionResponse} respuesta de obtención
     */
    @Mapping(target = "id", source = "prestacion.id")
    @Mapping(target = "codigo", source = "prestacion.codigo")
    @Mapping(target = "nombre", source = "prestacion.nombre")
    @Mapping(target = "duracionMinimaMinutos", source = "prestacion.duracionMinima", qualifiedByName = "toMinutos")
    @Mapping(target = "duracionMaximaMinutos", source = "prestacion.duracionMaxima", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaSolicitudMinutos", source = "prestacion.tiempoToleranciaSolicitud", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaValidacionMinutos", source = "prestacion.tiempoToleranciaValidacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaReprogramacionMinutos", source = "prestacion.tiempoToleranciaReprogramacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaConfirmacionMinutos", source = "prestacion.tiempoToleranciaConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaCancelacionMinutos", source = "prestacion.tiempoToleranciaCancelacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaAnuncioMinutos", source = "prestacion.tiempoToleranciaAnuncio", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoRecordatorioConfirmacionMinutos", source = "prestacion.tiempoRecordatorioConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "especialidadId", source = "prestacion.especialidad.id")
    @Mapping(target = "especialidadNombre", source = "prestacion.especialidad.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    @Mapping(target = "indicaciones", source = "indicaciones")
    GetPrestacionResponse toGetResponse(Prestacion prestacion, List<GetIndicacionPrestacionResponse> indicaciones, EstadoPrestacion estadoVigente);

    /**
     * Convierte una entidad {@code Prestacion} a {@code ListPrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param estadoVigente {@code EstadoPrestacion} estado vigente calculado del histórico
     * @return {@code ListPrestacionResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "prestacion.id")
    @Mapping(target = "codigo", source = "prestacion.codigo")
    @Mapping(target = "nombre", source = "prestacion.nombre")
    @Mapping(target = "especialidadId", source = "prestacion.especialidad.id")
    @Mapping(target = "especialidadNombre", source = "prestacion.especialidad.nombre")
    @Mapping(target = "estadoActual", source = "estadoVigente")
    ListPrestacionResponse toListResponse(Prestacion prestacion, EstadoPrestacion estadoVigente);

    /**
     * Convierte un {@link Integer} (minutos) a {@link Duration}.
     *
     * @param minutos {@code Integer} minutos
     * @return {@code Duration} duración, o {@code null} si el parámetro es nulo
     */
    @Named("toDuration")
    default Duration toDuration(Integer minutos) {
        return minutos == null ? null : Duration.ofMinutes(minutos);
    }

    /**
     * Convierte un {@link Duration} a {@link Integer} (minutos).
     *
     * @param duracion {@code Duration} duración
     * @return {@code Integer} minutos, o {@code null} si el parámetro es nulo
     */
    @Named("toMinutos")
    default Integer toMinutos(Duration duracion) {
        return duracion == null ? null : (int) duracion.toMinutes();
    }

}
