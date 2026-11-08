package com.accesmed.backend.Records.Agente.Response;

/**
 * Prestación publicada, identificada por su nombre.
 *
 * @param nombre {@code String} nombre de la prestación
 */
public record ListPrestacionAgenteResponse(
        String nombre
) {
}
