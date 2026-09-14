package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Fila de {@code listAgendaMedico}: alimenta el selector de agendas del front, con los
 * conteos ya resueltos para no obligar a expandir días/horarios en el listado.
 *
 * @param id {@code UUID} identificador de la agenda
 * @param medicoId {@code UUID} identificador del médico dueño de la agenda
 * @param medicoNombre {@code String} nombre del médico dueño de la agenda
 * @param medicoApellido {@code String} apellido del médico dueño de la agenda
 * @param fechaHoraInicioVigencia {@code ZonedDateTime} inicio del período de vigencia
 * @param fechaHoraFinVigencia {@code ZonedDateTime} fin del período de vigencia
 * @param cantidadDias {@code long} cantidad de días activos de la agenda
 * @param cantidadHorarios {@code long} cantidad de horarios activos de la agenda
 */
public record ListAgendaMedicoResponse(
        UUID id,
        UUID medicoId,
        String medicoNombre,
        String medicoApellido,
        ZonedDateTime fechaHoraInicioVigencia,
        ZonedDateTime fechaHoraFinVigencia,
        long cantidadDias,
        long cantidadHorarios
) {

}
