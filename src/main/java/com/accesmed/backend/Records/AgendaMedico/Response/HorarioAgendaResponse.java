package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Slot de agenda anidado, usado por {@code DiaAgendaResponse} dentro de las respuestas de
 * {@code createAgendaMedico} y {@code getAgendaMedico}.
 *
 * @param id {@code UUID} identificador del {@code AgendaHorariosDia}
 * @param horaDesde {@code LocalTime} inicio del slot
 * @param horaHasta {@code LocalTime} fin del slot
 * @param prestacionId {@code UUID} identificador de la prestación del slot
 * @param prestacionNombre {@code String} nombre de la prestación del slot
 * @param fechaLimiteReserva {@code ZonedDateTime} plazo límite para reservar el slot
 * @param estaOcupada {@code Boolean} si el slot ya tiene un turno asociado
 */
public record HorarioAgendaResponse(
        UUID id,
        LocalTime horaDesde,
        LocalTime horaHasta,
        UUID prestacionId,
        String prestacionNombre,
        ZonedDateTime fechaLimiteReserva,
        Boolean estaOcupada
) {

}
