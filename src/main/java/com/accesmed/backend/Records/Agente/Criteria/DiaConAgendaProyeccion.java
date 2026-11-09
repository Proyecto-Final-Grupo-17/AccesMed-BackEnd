package com.accesmed.backend.Records.Agente.Criteria;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Día en que un médico tiene agenda, con cuántos horarios todavía se pueden reservar.
 *
 * @param medicoId {@code UUID} identificador del médico
 * @param fecha {@code LocalDate} día con horarios cargados
 * @param horariosLibres {@code long} horarios libres y dentro del plazo de reserva ese día
 */
public record DiaConAgendaProyeccion(UUID medicoId, LocalDate fecha, long horariosLibres) {
}
