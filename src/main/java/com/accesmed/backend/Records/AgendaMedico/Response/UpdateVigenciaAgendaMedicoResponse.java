package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de {@code updateVigenciaAgendaMedico}. No estaba en la lista original de
 * records de la Fase B (el plan enumeraba el resto de responses pero no este); se agrega
 * porque el endpoint lo necesita igual que los demás.
 *
 * @param id {@code UUID} identificador de la agenda
 * @param fechaInicioVigencia {@code LocalDate} inicio de vigencia vigente tras la actualización
 * @param fechaFinVigencia {@code LocalDate} fin de vigencia vigente tras la actualización
 * @param cantidadHorariosDadosDeBaja {@code int} cantidad de {@code AgendaHorariosDia} posteriores
 *        dados de baja al adelantar el fin (0 si no se adelantó el fin)
 */
public record UpdateVigenciaAgendaMedicoResponse(
        UUID id,
        LocalDate fechaInicioVigencia,
        LocalDate fechaFinVigencia,
        int cantidadHorariosDadosDeBaja
) {

}
