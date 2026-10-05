package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.LocalDate;
import java.util.List;

/**
 * Día de agenda anidado, usado por las respuestas de {@code createAgendaMedico} y
 * {@code getAgendaMedico} para exponer la agenda con sus días y horarios expandidos.
 * Se arma agrupando los {@code AgendaHorariosDia} activos por {@code fecha}: el día ya no
 * es una entidad propia, así que no tiene un identificador propio que exponer.
 *
 * @param fecha {@code LocalDate} fecha del día
 * @param horarios {@code List<HorarioAgendaResponse>} slots activos de ese día
 */
public record DiaAgendaResponse(
        LocalDate fecha,
        List<HorarioAgendaResponse> horarios
) {

}
