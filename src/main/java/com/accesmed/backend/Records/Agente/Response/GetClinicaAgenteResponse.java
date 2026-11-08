package com.accesmed.backend.Records.Agente.Response;

import java.time.LocalTime;

/**
 * Información general de la clínica que el agente le muestra al paciente.
 *
 * @param nombre {@code String} nombre de la clínica
 * @param descripcion {@code String} descripción
 * @param ubicacion {@code String} dirección
 * @param telefono {@code String} teléfono de contacto
 * @param email {@code String} email de contacto
 * @param horarioInicioAtencion {@code LocalTime} hora de apertura
 * @param horarioFinAtencion {@code LocalTime} hora de cierre
 */
public record GetClinicaAgenteResponse(
        String nombre,
        String descripcion,
        String ubicacion,
        String telefono,
        String email,
        LocalTime horarioInicioAtencion,
        LocalTime horarioFinAtencion
) {
}
