package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta del alta de un período de agenda: la agenda con sus días y horarios ya
 * expandidos a partir del patrón semanal o los días sueltos del request.
 *
 * @param id {@code UUID} identificador de la agenda creada
 * @param medicoId {@code UUID} identificador del médico dueño de la agenda
 * @param fechaHoraInicioVigencia {@code ZonedDateTime} inicio del período de vigencia
 * @param fechaHoraFinVigencia {@code ZonedDateTime} fin del período de vigencia
 * @param dias {@code List<DiaAgendaResponse>} días generados, con sus horarios
 * @param cantidadHorariosGenerados {@code int} cantidad total de slots generados
 */
public record CreateAgendaMedicoResponse(
        UUID id,
        UUID medicoId,
        ZonedDateTime fechaHoraInicioVigencia,
        ZonedDateTime fechaHoraFinVigencia,
        List<DiaAgendaResponse> dias,
        int cantidadHorariosGenerados
) {

}
