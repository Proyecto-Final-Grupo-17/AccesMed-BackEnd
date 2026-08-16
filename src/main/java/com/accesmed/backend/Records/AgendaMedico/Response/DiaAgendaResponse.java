package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Día de agenda anidado, usado por las respuestas de {@code createAgendaMedico} y
 * {@code getAgendaMedico} para exponer la agenda con sus días y horarios expandidos.
 *
 * @param id {@code UUID} identificador del {@code AgendaDia}
 * @param fecha {@code LocalDate} fecha del día
 * @param horarios {@code List<HorarioAgendaResponse>} slots activos de ese día
 */
public record DiaAgendaResponse(
        UUID id,
        LocalDate fecha,
        List<HorarioAgendaResponse> horarios
) {

}
