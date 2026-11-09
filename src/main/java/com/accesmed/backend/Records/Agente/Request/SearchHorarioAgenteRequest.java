package com.accesmed.backend.Records.Agente.Request;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Filtros de la búsqueda de horarios disponibles del canal chatbot ({@code GET /Agente/Horario}).
 * Todos son opcionales, pero hace falta al menos uno de {@code medico}, {@code prestacion} o
 * {@code especialidad}. Los nombres se resuelven por parecido; el día se pide con {@code fecha}, con
 * un rango ({@code fechaDesde}/{@code fechaHasta}) o con {@code diaSemana}.
 *
 * @param medico {@code String} nombre o apellido aproximado del médico, o su matrícula
 * @param prestacion {@code String} nombre aproximado de la prestación
 * @param especialidad {@code String} nombre aproximado de la especialidad
 * @param fecha {@code LocalDate} día puntual ({@code AAAA-MM-DD}); si viene, ignora el rango
 * @param fechaDesde {@code LocalDate} primer día del rango, inclusive
 * @param fechaHasta {@code LocalDate} último día del rango, inclusive
 * @param diaSemana {@code DiaSemanaAgente} día de la semana ("los jueves")
 * @param franjaHoraria {@code FranjaHorariaAgente} franja del día
 * @param horaDesde {@code LocalTime} hora de inicio mínima del horario ({@code HH:mm}), inclusive
 * @param horaHasta {@code LocalTime} hora de inicio máxima del horario ({@code HH:mm}), exclusive
 * @param pagina {@code Integer} página de días (0 la primera), para pedir más cuando la respuesta trae {@code hayMas}
 */
public record SearchHorarioAgenteRequest(
        String medico,
        String prestacion,
        String especialidad,
        LocalDate fecha,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        DiaSemanaAgente diaSemana,
        FranjaHorariaAgente franjaHoraria,
        LocalTime horaDesde,
        LocalTime horaHasta,
        Integer pagina
) {
}
