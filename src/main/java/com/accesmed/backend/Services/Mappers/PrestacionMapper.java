package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Request.CreatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdatePrestacionNoHabilitadaRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdateToleranciasPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Response.CreatePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.EnablePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.GetPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.SoftDeletePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionNoHabilitadaResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdateToleranciasPrestacionResponse;
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
    @Mapping(target = "fechaHabilitacion", ignore = true)
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    Prestacion toEntity(CreatePrestacionRequest createPrestacionRequest);

    /**
     * Actualiza el nombre de una prestación existente con datos de
     * {@code UpdatePrestacionNoHabilitadaRequest}. Un {@code nombre} en {@code null}
     * deja el campo sin tocar. La especialidad se resuelve y setea aparte en el App.
     *
     * @param prestacion {@code Prestacion} entidad a actualizar
     * @param updatePrestacionNoHabilitadaRequest {@code UpdatePrestacionNoHabilitadaRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "nombre", source = "updatePrestacionNoHabilitadaRequest.nombre")
    @Mapping(target = "duracionMinima", ignore = true)
    @Mapping(target = "duracionMaxima", ignore = true)
    @Mapping(target = "tiempoToleranciaSolicitud", ignore = true)
    @Mapping(target = "tiempoToleranciaValidacion", ignore = true)
    @Mapping(target = "tiempoToleranciaReprogramacion", ignore = true)
    @Mapping(target = "tiempoToleranciaConfirmacion", ignore = true)
    @Mapping(target = "tiempoToleranciaCancelacion", ignore = true)
    @Mapping(target = "tiempoToleranciaAnuncio", ignore = true)
    @Mapping(target = "tiempoRecordatorioConfirmacion", ignore = true)
    @Mapping(target = "fechaHabilitacion", ignore = true)
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updatePrestacionNoHabilitada(@MappingTarget Prestacion prestacion,
                                      UpdatePrestacionNoHabilitadaRequest updatePrestacionNoHabilitadaRequest);

    /**
     * Actualiza las duraciones y tolerancias de una prestación existente con datos de
     * {@code UpdateToleranciasPrestacionRequest}. Un campo en {@code null} deja esa
     * tolerancia/duración sin tocar.
     *
     * @param prestacion {@code Prestacion} entidad a actualizar
     * @param updateToleranciasPrestacionRequest {@code UpdateToleranciasPrestacionRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "nombre", ignore = true)
    @Mapping(target = "duracionMinima", source = "updateToleranciasPrestacionRequest.duracionMinimaMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "duracionMaxima", source = "updateToleranciasPrestacionRequest.duracionMaximaMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaSolicitud", source = "updateToleranciasPrestacionRequest.tiempoToleranciaSolicitudMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaValidacion", source = "updateToleranciasPrestacionRequest.tiempoToleranciaValidacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaReprogramacion", source = "updateToleranciasPrestacionRequest.tiempoToleranciaReprogramacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaConfirmacion", source = "updateToleranciasPrestacionRequest.tiempoToleranciaConfirmacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaCancelacion", source = "updateToleranciasPrestacionRequest.tiempoToleranciaCancelacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoToleranciaAnuncio", source = "updateToleranciasPrestacionRequest.tiempoToleranciaAnuncioMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "tiempoRecordatorioConfirmacion", source = "updateToleranciasPrestacionRequest.tiempoRecordatorioConfirmacionMinutos", qualifiedByName = "toDuration")
    @Mapping(target = "fechaHabilitacion", ignore = true)
    @Mapping(target = "especialidad", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    void updateToleranciasPrestacion(@MappingTarget Prestacion prestacion,
                                     UpdateToleranciasPrestacionRequest updateToleranciasPrestacionRequest);

    /**
     * Convierte una entidad {@code Prestacion} y su lista de indicaciones a {@code CreatePrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param indicaciones {@code List<GetIndicacionPrestacionResponse>} lista de indicaciones
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
    @Mapping(target = "fechaHabilitacion", source = "prestacion.fechaHabilitacion")
    @Mapping(target = "habilitada", expression = "java(prestacion.getFechaHabilitacion() != null)")
    @Mapping(target = "indicaciones", source = "indicaciones")
    CreatePrestacionResponse toCreateResponse(Prestacion prestacion, List<GetIndicacionPrestacionResponse> indicaciones);

    /**
     * Convierte una entidad {@code Prestacion} a {@code UpdatePrestacionNoHabilitadaResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @return {@code UpdatePrestacionNoHabilitadaResponse} respuesta de actualización de datos generales
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "especialidadId", source = "especialidad.id")
    @Mapping(target = "especialidadNombre", source = "especialidad.nombre")
    @Mapping(target = "habilitada", expression = "java(prestacion.getFechaHabilitacion() != null)")
    UpdatePrestacionNoHabilitadaResponse toUpdatePrestacionNoHabilitadaResponse(Prestacion prestacion);

    /**
     * Convierte una entidad {@code Prestacion} a {@code UpdateToleranciasPrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @return {@code UpdateToleranciasPrestacionResponse} respuesta de actualización de tolerancias
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "duracionMinimaMinutos", source = "duracionMinima", qualifiedByName = "toMinutos")
    @Mapping(target = "duracionMaximaMinutos", source = "duracionMaxima", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaSolicitudMinutos", source = "tiempoToleranciaSolicitud", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaValidacionMinutos", source = "tiempoToleranciaValidacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaReprogramacionMinutos", source = "tiempoToleranciaReprogramacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaConfirmacionMinutos", source = "tiempoToleranciaConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaCancelacionMinutos", source = "tiempoToleranciaCancelacion", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoToleranciaAnuncioMinutos", source = "tiempoToleranciaAnuncio", qualifiedByName = "toMinutos")
    @Mapping(target = "tiempoRecordatorioConfirmacionMinutos", source = "tiempoRecordatorioConfirmacion", qualifiedByName = "toMinutos")
    @Mapping(target = "habilitada", expression = "java(prestacion.getFechaHabilitacion() != null)")
    UpdateToleranciasPrestacionResponse toUpdateToleranciasResponse(Prestacion prestacion);

    /**
     * Convierte una entidad {@code Prestacion} a {@code EnablePrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad habilitada
     * @return {@code EnablePrestacionResponse} respuesta de habilitación
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "habilitada", expression = "java(prestacion.getFechaHabilitacion() != null)")
    EnablePrestacionResponse toEnableResponse(Prestacion prestacion);

    /**
     * Convierte una entidad {@code Prestacion} a {@code SoftDeletePrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad dada de baja
     * @return {@code SoftDeletePrestacionResponse} respuesta de baja lógica
     */
    SoftDeletePrestacionResponse toSoftDeleteResponse(Prestacion prestacion);

    /**
     * Convierte una entidad {@code Prestacion} y su lista de indicaciones a {@code GetPrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param indicaciones {@code List<GetIndicacionPrestacionResponse>} lista de indicaciones
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
    @Mapping(target = "fechaHabilitacion", source = "prestacion.fechaHabilitacion")
    @Mapping(target = "habilitada", expression = "java(prestacion.getFechaHabilitacion() != null)")
    @Mapping(target = "indicaciones", source = "indicaciones")
    GetPrestacionResponse toGetResponse(Prestacion prestacion, List<GetIndicacionPrestacionResponse> indicaciones);

    /**
     * Convierte una entidad {@code Prestacion} a {@code ListPrestacionResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @return {@code ListPrestacionResponse} respuesta de listado
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "codigo", source = "codigo")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "especialidadId", source = "especialidad.id")
    @Mapping(target = "especialidadNombre", source = "especialidad.nombre")
    @Mapping(target = "habilitada", expression = "java(prestacion.getFechaHabilitacion() != null)")
    ListPrestacionResponse toListResponse(Prestacion prestacion);

    /**
     * Convierte una lista de entidades {@code Prestacion} a una lista de {@code ListPrestacionResponse}.
     *
     * @param prestaciones {@code List<Prestacion>} lista de entidades
     * @return {@code List<ListPrestacionResponse>} lista de respuestas
     */
    List<ListPrestacionResponse> toListResponses(List<Prestacion> prestaciones);

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
