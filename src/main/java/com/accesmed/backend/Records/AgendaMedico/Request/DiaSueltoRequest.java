package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Día suelto de {@code createAgendaMedico}: alternativa al patrón semanal para agendas que
 * no siguen un patrón regular. Cada entrada es una fecha concreta con sus bloques horarios.
 *
 * @param fecha {@code LocalDate} fecha concreta del día
 * @param bloques {@code List<BloqueHorarioRequest>} bloques horarios de ese día
 */
public record DiaSueltoRequest(
        @NotNull LocalDate fecha,
        @NotEmpty List<@Valid BloqueHorarioRequest> bloques
) {

}
