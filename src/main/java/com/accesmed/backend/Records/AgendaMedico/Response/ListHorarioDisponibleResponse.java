package com.accesmed.backend.Records.AgendaMedico.Response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Fila de {@code listHorariosDisponibles}: el único listado que consume el chatbot.
 * Guardas fijas del {@code QueryService}: {@code estaOcupada = false},
 * {@code ahora < fechaLimiteReserva} y {@code fecha <= hoy + diasMaximosAnticipacionReserva}
 * de la clínica — el front no puede pedir un slot ocupado ni uno fuera del horizonte.
 *
 * @param id {@code UUID} identificador del {@code AgendaHorarios}
 * @param medicoId {@code UUID} identificador del médico
 * @param medicoNombre {@code String} nombre del médico
 * @param medicoApellido {@code String} apellido del médico
 * @param prestacionId {@code UUID} identificador de la prestación del slot
 * @param prestacionNombre {@code String} nombre de la prestación del slot
 * @param fecha {@code LocalDate} fecha del slot
 * @param horaDesde {@code LocalTime} inicio del slot
 * @param horaHasta {@code LocalTime} fin del slot
 * @param fechaLimiteReserva {@code ZonedDateTime} plazo límite para reservar el slot
 */
public record ListHorarioDisponibleResponse(
        UUID id,
        UUID medicoId,
        String medicoNombre,
        String medicoApellido,
        UUID prestacionId,
        String prestacionNombre,
        LocalDate fecha,
        LocalTime horaDesde,
        LocalTime horaHasta,
        ZonedDateTime fechaLimiteReserva
) {

}
