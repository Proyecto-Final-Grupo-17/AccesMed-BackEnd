package com.accesmed.backend.Records.AgendaMedico.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Fila de {@code listHorariosAgenda}: pinta el calendario del panel para el período de
 * agenda elegido. Guarda fija: solo horarios activos ({@code deletedAt} vacío).
 *
 * @param id {@code UUID} identificador del {@code AgendaHorariosDia}
 * @param fecha {@code LocalDate} fecha del día
 * @param horaDesde {@code LocalTime} inicio del slot
 * @param horaHasta {@code LocalTime} fin del slot
 * @param medicoId {@code UUID} identificador del médico de la agenda
 * @param prestacionId {@code UUID} identificador de la prestación del slot
 * @param prestacionNombre {@code String} nombre de la prestación del slot
 * @param fechaLimiteReserva {@code ZonedDateTime} plazo límite para reservar el slot
 * @param estaOcupada {@code Boolean} si el slot ya tiene un turno asociado
 * @param auditoria {@code AuditoriaResponse} datos de auditoría, solo poblado si quien consulta
 *         tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario
 */
public record ListAgendaHorarioResponse(
        UUID id,
        LocalDate fecha,
        LocalTime horaDesde,
        LocalTime horaHasta,
        UUID medicoId,
        UUID prestacionId,
        String prestacionNombre,
        ZonedDateTime fechaLimiteReserva,
        Boolean estaOcupada,
        AuditoriaResponse auditoria
) {

}
