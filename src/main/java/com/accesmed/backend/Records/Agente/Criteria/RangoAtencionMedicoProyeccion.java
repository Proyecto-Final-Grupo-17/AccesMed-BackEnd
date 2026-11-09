package com.accesmed.backend.Records.Agente.Criteria;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Rango horario en el que un médico atiende un día (del primer inicio al último fin de sus horarios
 * cargados, estén libres u ocupados).
 *
 * @param medicoId {@code UUID} identificador del médico
 * @param fecha {@code LocalDate} día
 * @param desde {@code LocalTime} inicio del primer horario del día
 * @param hasta {@code LocalTime} fin del último horario del día
 */
public record RangoAtencionMedicoProyeccion(UUID medicoId, LocalDate fecha, LocalTime desde, LocalTime hasta) {
}
