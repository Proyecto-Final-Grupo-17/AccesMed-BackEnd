package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.EstadoTurno;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Turno creado desde el canal. Solo expone claves de negocio, sin ids internos.
 *
 * @param codigo {@code String} código del turno
 * @param fechaHoraInicio {@code ZonedDateTime} fecha y hora de inicio
 * @param nombrePrestacion {@code String} nombre de la prestación
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param montoAPagar {@code BigDecimal} monto a pagar por el paciente
 * @param tipoCobertura {@code String} tipo de cobertura ({@code PARTICULAR} u {@code OBRA_SOCIAL})
 * @param estadoActual {@code EstadoTurno} estado vigente del turno
 */
public record CreateTurnoAgenteResponse(
        String codigo,
        ZonedDateTime fechaHoraInicio,
        String nombrePrestacion,
        String nombreMedico,
        String apellidoMedico,
        BigDecimal montoAPagar,
        String tipoCobertura,
        EstadoTurno estadoActual
) {
}
