package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Bloque horario dentro de un día del patrón semanal o de un día suelto, en
 * {@code createAgendaMedico}. Se expande a slots de {@code duracionTurno} entre
 * {@code horaDesde} y {@code horaHasta} para la prestación indicada.
 *
 * @param horaDesde {@code LocalTime} inicio del bloque
 * @param horaHasta {@code LocalTime} fin del bloque
 * @param prestacionId {@code UUID} identificador de la prestación de los slots del bloque
 * @param duracionTurno {@code Duration} duración de cada slot dentro del bloque
 */
public record BloqueHorarioRequest(
        @NotNull LocalTime horaDesde,
        @NotNull LocalTime horaHasta,
        @NotNull UUID prestacionId,
        @NotNull Duration duracionTurno
) {

}
