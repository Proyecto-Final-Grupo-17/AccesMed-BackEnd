package com.accesmed.backend.Records.Agente.Response;

import java.util.List;

/**
 * Oferta de una especialidad: prestaciones publicadas y médicos activos.
 *
 * @param especialidad {@code String} nombre de la especialidad
 * @param prestaciones {@code List<ListPrestacionAgenteResponse>} prestaciones publicadas
 * @param medicos {@code List<ListMedicoAgenteResponse>} médicos activos
 */
public record GetOfertaEspecialidadAgenteResponse(
        String especialidad,
        List<ListPrestacionAgenteResponse> prestaciones,
        List<ListMedicoAgenteResponse> medicos
) {
}
