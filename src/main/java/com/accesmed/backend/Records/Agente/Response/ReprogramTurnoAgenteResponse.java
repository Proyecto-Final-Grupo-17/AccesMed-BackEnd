package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.EstadoTurno;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Turno nuevo resultante de reprogramar, enlazado al turno original.
 *
 * @param codigo {@code String} código del turno
 * @param fechaHoraInicio {@code LocalDateTime} fecha y hora de inicio en la zona horaria de la clínica (sin zona: es la hora que ve el paciente)
 * @param fechaHoraTexto {@code String} la misma fecha y hora legible, con el día de la semana ({@code jueves 12/11/2026 09:30}); el agente la copia tal cual
 * @param nombrePrestacion {@code String} nombre de la prestación
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param montoAPagar {@code BigDecimal} monto a pagar por el paciente
 * @param tipoCobertura {@code String} tipo de cobertura ({@code PARTICULAR} u {@code OBRA_SOCIAL})
 * @param estadoActual {@code EstadoTurno} estado vigente del turno
 * @param codigoTurnoOrigen {@code String} código del turno reprogramado
 */
public record ReprogramTurnoAgenteResponse(
        String codigo,
        LocalDateTime fechaHoraInicio,
        String fechaHoraTexto,
        String nombrePrestacion,
        String nombreMedico,
        String apellidoMedico,
        String matricula,
        BigDecimal montoAPagar,
        String tipoCobertura,
        EstadoTurno estadoActual,
        String codigoTurnoOrigen
) {
}
