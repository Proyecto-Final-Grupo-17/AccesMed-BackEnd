package com.accesmed.backend.Records.Agente.Response;

import java.time.LocalDate;

/**
 * Día en que un médico tiene agenda.
 *
 * @param fecha {@code LocalDate} día con agenda
 * @param fechaTexto {@code String} el día legible, con el día de la semana ({@code jueves 12/11/2026}); el agente lo copia tal cual
 * @param hayHorariosLibres {@code boolean} {@code true} si ese día queda algún horario para reservar
 */
public record DiaAgendaAgenteResponse(
        LocalDate fecha,
        String fechaTexto,
        boolean hayHorariosLibres
) {
}
