package com.accesmed.backend.Records.Agente.Criteria;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Un horario libre con los datos del médico y la prestación, leído en una sola consulta (sin cargar
 * entidades) para agrupar la respuesta de la búsqueda de horarios del canal chatbot.
 *
 * @param slotId {@code UUID} identificador del horario
 * @param fecha {@code LocalDate} día del horario
 * @param horaDesde {@code LocalTime} hora de inicio
 * @param medicoId {@code UUID} identificador del médico
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param especialidad {@code String} nombre de la especialidad del médico
 * @param prestacionId {@code UUID} identificador de la prestación
 * @param nombrePrestacion {@code String} nombre de la prestación
 */
public record HorarioAgenteProyeccion(
        UUID slotId,
        LocalDate fecha,
        LocalTime horaDesde,
        UUID medicoId,
        String nombreMedico,
        String apellidoMedico,
        String matricula,
        String especialidad,
        UUID prestacionId,
        String nombrePrestacion
) {
}
