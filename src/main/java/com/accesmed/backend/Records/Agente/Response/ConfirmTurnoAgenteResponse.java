package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.EstadoTurno;

import java.time.LocalDateTime;

/**
 * Turno confirmado desde el canal.
 *
 * @param codigo {@code String} código del turno
 * @param fechaHoraInicio {@code LocalDateTime} fecha y hora de inicio en la zona horaria de la clínica (sin zona: es la hora que ve el paciente)
 * @param nombrePrestacion {@code String} nombre de la prestación
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param estadoActual {@code EstadoTurno} estado vigente ({@code CONFIRMADO})
 */
public record ConfirmTurnoAgenteResponse(
        String codigo,
        LocalDateTime fechaHoraInicio,
        String nombrePrestacion,
        String nombreMedico,
        String apellidoMedico,
        String matricula,
        EstadoTurno estadoActual
) {
}
