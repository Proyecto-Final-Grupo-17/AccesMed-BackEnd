package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.EstadoTurno;

import java.time.ZonedDateTime;

/**
 * Turno confirmado desde el canal.
 *
 * @param codigo {@code String} código del turno
 * @param fechaHoraInicio {@code ZonedDateTime} fecha y hora de inicio
 * @param nombrePrestacion {@code String} nombre de la prestación
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param estadoActual {@code EstadoTurno} estado vigente ({@code CONFIRMADO})
 */
public record ConfirmTurnoAgenteResponse(
        String codigo,
        ZonedDateTime fechaHoraInicio,
        String nombrePrestacion,
        String nombreMedico,
        String apellidoMedico,
        EstadoTurno estadoActual
) {
}
