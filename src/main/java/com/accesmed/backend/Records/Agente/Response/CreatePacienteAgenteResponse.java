package com.accesmed.backend.Records.Agente.Response;

/**
 * Confirmación del registro de un paciente desde el canal (alcanza para el mensaje de bienvenida).
 *
 * @param nombre {@code String} nombre del paciente registrado
 * @param apellido {@code String} apellido del paciente registrado
 */
public record CreatePacienteAgenteResponse(
        String nombre,
        String apellido
) {
}
