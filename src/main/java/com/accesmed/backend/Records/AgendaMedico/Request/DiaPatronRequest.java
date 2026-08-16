package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Día del patrón semanal de {@code createAgendaMedico}: se repite en cada
 * {@code diaSemana} que caiga dentro del período de vigencia de la agenda. El patrón no se
 * persiste: se expande a {@code AgendaDia} + {@code AgendaHorarios} y se descarta.
 *
 * @param diaSemana {@code DayOfWeek} día de la semana en el que se repite este bloque de horarios
 * @param bloques {@code List<BloqueHorarioRequest>} bloques horarios de ese día de la semana
 */
public record DiaPatronRequest(
        @NotNull DayOfWeek diaSemana,
        @NotEmpty List<@Valid BloqueHorarioRequest> bloques
) {

}
