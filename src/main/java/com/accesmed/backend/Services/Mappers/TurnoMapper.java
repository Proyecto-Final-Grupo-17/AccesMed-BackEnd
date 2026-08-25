package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Records.Turno.Response.CancelTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.CreateTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ListTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ReprogramTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ValidateTurnoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper para la entidad {@code Turno}. Realiza conversiones entre
 * records de request/response y la entidad JPA.
 *
 * Nota: el estado se alimenta desde el parámetro {@code estadoActual} que el App
 * calcula del histórico y pasa al mapper. Las relaciones (paciente, médico,
 * prestación, etc.) se setean en el App después de validaciones previas.
 */
@Mapper(componentModel = "spring")
public interface TurnoMapper {

    /**
     * Convierte una entidad {@code Turno} a {@code CreateTurnoResponse}.
     *
     * @param turno {@code Turno} entidad
     * @param estadoActual {@code EstadoTurno} estado vigente del turno
     * @return {@code CreateTurnoResponse} respuesta de creación
     */
    @Mapping(target = "id", source = "turno.id")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "pacienteId", source = "turno.paciente.id")
    @Mapping(target = "medicoId", source = "turno.medico.id")
    @Mapping(target = "prestacionId", source = "turno.prestacion.id")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura", qualifiedByName = "tipoCoberturaToString")
    @Mapping(target = "estadoActual", source = "estadoActual")
    CreateTurnoResponse toCreateResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte una entidad {@code Turno} a {@code ReprogramTurnoResponse}.
     *
     * @param turno {@code Turno} entidad del turno nuevo (reprogramado)
     * @param estadoActual {@code EstadoTurno} estado vigente del turno nuevo
     * @return {@code ReprogramTurnoResponse} respuesta de reprogramación
     */
    @Mapping(target = "id", source = "turno.id")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "pacienteId", source = "turno.paciente.id")
    @Mapping(target = "medicoId", source = "turno.medico.id")
    @Mapping(target = "prestacionId", source = "turno.prestacion.id")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura", qualifiedByName = "tipoCoberturaToString")
    @Mapping(target = "estadoActual", source = "estadoActual")
    @Mapping(target = "turnoOrigenId", source = "turno.turnoOrigen.id")
    ReprogramTurnoResponse toReprogramResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte una entidad {@code Turno} a {@code CancelTurnoResponse}.
     *
     * @param turno {@code Turno} entidad
     * @param estadoActual {@code EstadoTurno} estado vigente del turno
     * @return {@code CancelTurnoResponse} respuesta de cancelación
     */
    @Mapping(target = "id", source = "turno.id")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "pacienteId", source = "turno.paciente.id")
    @Mapping(target = "medicoId", source = "turno.medico.id")
    @Mapping(target = "prestacionId", source = "turno.prestacion.id")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura", qualifiedByName = "tipoCoberturaToString")
    @Mapping(target = "estadoActual", source = "estadoActual")
    @Mapping(target = "motivoCancelacion", source = "turno.motivoCancelacion")
    CancelTurnoResponse toCancelResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte una entidad {@code Turno} a {@code ValidateTurnoResponse}.
     *
     * @param turno {@code Turno} entidad
     * @param estadoActual {@code EstadoTurno} estado vigente del turno
     * @return {@code ValidateTurnoResponse} respuesta de validación
     */
    @Mapping(target = "id", source = "turno.id")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "pacienteId", source = "turno.paciente.id")
    @Mapping(target = "medicoId", source = "turno.medico.id")
    @Mapping(target = "prestacionId", source = "turno.prestacion.id")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura", qualifiedByName = "tipoCoberturaToString")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ValidateTurnoResponse toValidateResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte una entidad {@code Turno} a {@code ListTurnoResponse}.
     *
     * @param turno {@code Turno} entidad
     * @param estadoActual {@code EstadoTurno} estado vigente del turno
     * @return {@code ListTurnoResponse} respuesta simplificada para listados
     */
    @Mapping(target = "id", source = "turno.id")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "pacienteId", source = "turno.paciente.id")
    @Mapping(target = "medicoId", source = "turno.medico.id")
    @Mapping(target = "prestacionId", source = "turno.prestacion.id")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura", qualifiedByName = "tipoCoberturaToString")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ListTurnoResponse toListResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte un {@code TipoCobertura} enum a {@code String}.
     *
     * @param tipoCobertura {@code TipoCobertura} enumeración
     * @return {@code String} nombre del enum
     */
    @org.mapstruct.Named("tipoCoberturaToString")
    default String tipoCoberturaToString(com.accesmed.backend.Domain.TipoCobertura tipoCobertura) {
        return tipoCobertura != null ? tipoCobertura.name() : null;
    }

}
