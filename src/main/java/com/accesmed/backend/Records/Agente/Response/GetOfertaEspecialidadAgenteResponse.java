package com.accesmed.backend.Records.Agente.Response;

import java.util.List;

/**
 * Oferta de una especialidad: sus prestaciones publicadas, cada una con los médicos que la
 * atienden. Las prestaciones sin ningún médico vigente se omiten (no se pueden reservar).
 *
 * @param especialidad {@code String} nombre de la especialidad
 * @param prestaciones {@code List<ListPrestacionAgenteResponse>} prestaciones publicadas con sus médicos
 */
public record GetOfertaEspecialidadAgenteResponse(
        String especialidad,
        List<ListPrestacionAgenteResponse> prestaciones
) {
}
