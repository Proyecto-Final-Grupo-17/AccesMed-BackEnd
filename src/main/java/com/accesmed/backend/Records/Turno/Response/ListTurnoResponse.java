package com.accesmed.backend.Records.Turno.Response;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response simplificado de un turno para listados paginados.
 *
 * @param id identificador único del turno
 * @param codigo código único del turno
 * @param pacienteId identificador del paciente
 * @param medicoId identificador del médico
 * @param prestacionId identificador de la prestación
 * @param fechaHoraInicio fecha y hora de inicio del turno
 * @param montoAPagar monto a abonar por el turno
 * @param tipoCobertura tipo de cobertura
 * @param estadoActual estado vigente del turno
 * @param auditoria datos de auditoría, solo poblado si quien consulta tiene AUDITORIA_CONSULTAR
 */
public record ListTurnoResponse(
        UUID id,
        String codigo,
        UUID pacienteId,
        UUID medicoId,
        UUID prestacionId,
        ZonedDateTime fechaHoraInicio,
        BigDecimal montoAPagar,
        String tipoCobertura,
        EstadoTurno estadoActual,
        AuditoriaResponse auditoria
) {}
