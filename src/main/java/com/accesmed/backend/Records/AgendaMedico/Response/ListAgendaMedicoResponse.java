package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila de {@code listAgendaMedico}: alimenta el selector de agendas del front, con los
 * conteos ya resueltos para no obligar a expandir días/horarios en el listado.
 *
 * @param id {@code UUID} identificador de la agenda
 * @param medicoId {@code UUID} identificador del médico dueño de la agenda
 * @param medicoNombre {@code String} nombre del médico dueño de la agenda
 * @param medicoApellido {@code String} apellido del médico dueño de la agenda
 * @param fechaInicioVigencia {@code LocalDate} inicio del período de vigencia
 * @param fechaFinVigencia {@code LocalDate} fin del período de vigencia
 * @param cantidadDias {@code long} cantidad de días activos de la agenda
 * @param cantidadHorarios {@code long} cantidad de horarios activos de la agenda
 */
public record ListAgendaMedicoResponse(
        UUID id,
        UUID medicoId,
        String medicoNombre,
        String medicoApellido,
        LocalDate fechaInicioVigencia,
        LocalDate fechaFinVigencia,
        long cantidadDias,
        long cantidadHorarios
) {

}
