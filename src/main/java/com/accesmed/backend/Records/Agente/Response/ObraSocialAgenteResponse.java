package com.accesmed.backend.Records.Agente.Response;

/**
 * Cobertura de obra social de un paciente, expresada con nombres (sin ids).
 *
 * @param obraSocialNombre {@code String} nombre de la obra social
 * @param planNombre {@code String} nombre del plan
 * @param nroSocio {@code String} número de socio
 */
public record ObraSocialAgenteResponse(
        String obraSocialNombre,
        String planNombre,
        String nroSocio
) {
}
