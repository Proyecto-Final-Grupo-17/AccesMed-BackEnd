package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Respuesta de {@code updateVigenciaAgendaMedico}. No estaba en la lista original de
 * records de la Fase B (el plan enumeraba el resto de responses pero no este); se agrega
 * porque el endpoint lo necesita igual que los demás.
 *
 * @param id {@code UUID} identificador de la agenda
 * @param fechaHoraInicioVigencia {@code ZonedDateTime} inicio de vigencia vigente tras la actualización
 * @param fechaHoraFinVigencia {@code ZonedDateTime} fin de vigencia vigente tras la actualización
 * @param cantidadDiasDadosDeBaja {@code int} cantidad de {@code AgendaDia} posteriores dados de baja
 *        al adelantar el fin (0 si no se adelantó el fin)
 */
public record UpdateVigenciaAgendaMedicoResponse(
        UUID id,
        ZonedDateTime fechaHoraInicioVigencia,
        ZonedDateTime fechaHoraFinVigencia,
        int cantidadDiasDadosDeBaja
) {

}
