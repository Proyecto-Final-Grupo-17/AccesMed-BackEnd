package com.accesmed.backend.Records.Agente.Response;

import java.util.List;

/**
 * Obra social activa con los planes que un paciente puede declarar como cobertura (los no
 * deshabilitados), expresada con nombres: son los valores que acepta {@code ObraSocialAgenteRequest}.
 *
 * @param nombre {@code String} nombre de la obra social
 * @param planes {@code List<String>} nombres de sus planes no deshabilitados
 */
public record ListObraSocialAgenteResponse(
        String nombre,
        List<String> planes
) {
}
