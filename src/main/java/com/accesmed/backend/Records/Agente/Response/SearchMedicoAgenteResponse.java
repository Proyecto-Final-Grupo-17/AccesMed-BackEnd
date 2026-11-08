package com.accesmed.backend.Records.Agente.Response;

import java.util.List;

/**
 * Médico activo encontrado por parecido con su nombre o apellido, con su especialidad y las
 * prestaciones publicadas que atiende hoy.
 *
 * @param nombre {@code String} nombre del médico
 * @param apellido {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param especialidad {@code String} nombre de su especialidad
 * @param prestaciones {@code List<String>} nombres de las prestaciones publicadas que atiende
 */
public record SearchMedicoAgenteResponse(
        String nombre,
        String apellido,
        String matricula,
        String especialidad,
        List<String> prestaciones
) {
}
