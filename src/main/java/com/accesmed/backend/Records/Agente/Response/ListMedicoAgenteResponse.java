package com.accesmed.backend.Records.Agente.Response;

/**
 * Médico activo, identificado por su matrícula.
 *
 * @param nombre {@code String} nombre del médico
 * @param apellido {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 */
public record ListMedicoAgenteResponse(
        String nombre,
        String apellido,
        String matricula
) {
}
