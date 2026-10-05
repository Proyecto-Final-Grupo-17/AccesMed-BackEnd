package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Bloque horario a agregar en {@code updateAgendaMedico}. El día es implícito en
 * {@code fecha}: no hace falta una entidad de día propia, los {@code AgendaHorariosDia}
 * nuevos se insertan directo con esa fecha.
 *
 * @param fecha {@code LocalDate} día al que pertenece el bloque
 * @param horaDesde {@code LocalTime} inicio del bloque
 * @param horaHasta {@code LocalTime} fin del bloque
 * @param prestacionId {@code UUID} identificador de la prestación de los slots del bloque
 * @param duracionTurno {@code Duration} duración de cada slot dentro del bloque
 */
public record HorarioAAgregarRequest(
        @NotNull LocalDate fecha,
        @NotNull LocalTime horaDesde,
        @NotNull LocalTime horaHasta,
        @NotNull UUID prestacionId,
        @NotNull Duration duracionTurno
) {

}
