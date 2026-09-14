package com.accesmed.backend.Records.IndicacionPrestacionTurno.Response;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response para obtener una indicación de prestación de turno individual por su id.
 *
 * @param id {@code UUID} identificador de la indicación de turno
 * @param turnoId {@code UUID} identificador del turno
 * @param indicacionPrestacionId {@code UUID} identificador de la indicación de prestación
 * @param indicacionPrestacionNombre {@code String} nombre de la indicación (vía relación)
 * @param indicacionPrestacionDescripcion {@code String} descripción de la indicación (vía relación)
 * @param requiereValidacion {@code boolean} si la indicación requiere validación (vía relación)
 * @param fechaHoraValidacion {@code ZonedDateTime} fecha y hora de validación, o {@code null} si no ha sido validada
 * @param validadoPorId {@code UUID} id del admin que validó, o {@code null} si no ha sido validada
 */
public record GetIndicacionPrestacionTurnoResponse(
        UUID id,
        UUID turnoId,
        UUID indicacionPrestacionId,
        String indicacionPrestacionNombre,
        String indicacionPrestacionDescripcion,
        boolean requiereValidacion,
        ZonedDateTime fechaHoraValidacion,
        UUID validadoPorId
) {
}
