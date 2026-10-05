package com.accesmed.backend.Records.Turno.Response;

import com.accesmed.backend.Domain.EstadoTurno;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response con los datos del turno reprogramado.
 *
 * @param id identificador único del turno nuevo
 * @param codigo código único del turno nuevo
 * @param pacienteId identificador del paciente
 * @param medicoId identificador del médico
 * @param prestacionId identificador de la prestación
 * @param fechaHoraInicio fecha y hora de inicio del nuevo turno
 * @param montoAPagar monto a abonar por el turno
 * @param tipoCobertura tipo de cobertura
 * @param estadoActual estado vigente del turno nuevo
 * @param turnoOrigenId identificador del turno original
 */
public record ReprogramTurnoResponse(
        UUID id,
        String codigo,
        UUID pacienteId,
        UUID medicoId,
        UUID prestacionId,
        ZonedDateTime fechaHoraInicio,
        BigDecimal montoAPagar,
        String tipoCobertura,
        EstadoTurno estadoActual,
        UUID turnoOrigenId
) {}
