package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Services.Utils.CoincidenciaBusqueda;

import java.util.List;

/**
 * Médico activo encontrado por parecido con su nombre o apellido (o por especialidad o prestación),
 * con su especialidad, las prestaciones publicadas que atiende hoy y los días en que tiene agenda.
 *
 * @param nombre {@code String} nombre del médico
 * @param apellido {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param especialidad {@code String} nombre de su especialidad
 * @param prestaciones {@code List<String>} nombres de las prestaciones publicadas que atiende
 * @param coincidencia {@code CoincidenciaBusqueda} {@code EXACTA} si coincide lo que escribió el paciente, {@code APROXIMADA} si es solo parecido (puede ser otra cosa)
 * @param haceLaPrestacion {@code Boolean} si hace alguna de las prestaciones pedidas con el filtro {@code prestacion}, o {@code null} si no se pidió ninguna
 * @param atiendeParticular {@code Boolean} si atiende particular alguna de las prestaciones pedidas, o {@code null} si no se pidió ninguna
 * @param proximosDiasConAgenda {@code List<DiaAgendaAgenteResponse>} días con agenda en las próximas semanas, en orden
 */
public record SearchMedicoAgenteResponse(
        String nombre,
        String apellido,
        String matricula,
        String especialidad,
        List<String> prestaciones,
        CoincidenciaBusqueda coincidencia,
        Boolean haceLaPrestacion,
        Boolean atiendeParticular,
        List<DiaAgendaAgenteResponse> proximosDiasConAgenda
) {
}
