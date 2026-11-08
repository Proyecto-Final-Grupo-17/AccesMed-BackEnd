package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.EstadoTurno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turno creado desde el canal. Solo expone claves de negocio, sin ids internos.
 *
 * @param codigo {@code String} código del turno
 * @param fechaHoraInicio {@code LocalDateTime} fecha y hora de inicio en la zona horaria de la clínica (sin zona: es la hora que ve el paciente)
 * @param nombrePrestacion {@code String} nombre de la prestación
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param montoAPagar {@code BigDecimal} monto a pagar por el paciente
 * @param tipoCobertura {@code String} tipo de cobertura ({@code PARTICULAR} u {@code OBRA_SOCIAL})
 * @param estadoActual {@code EstadoTurno} estado vigente del turno ({@code ESPERA_VALIDACION} si alguna indicación lo requiere)
 * @param indicaciones {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones que el paciente tiene que cumplir
 */
public record CreateTurnoAgenteResponse(
        String codigo,
        LocalDateTime fechaHoraInicio,
        String nombrePrestacion,
        String nombreMedico,
        String apellidoMedico,
        String matricula,
        BigDecimal montoAPagar,
        String tipoCobertura,
        EstadoTurno estadoActual,
        List<ListIndicacionPrestacionAgenteResponse> indicaciones
) {
}
