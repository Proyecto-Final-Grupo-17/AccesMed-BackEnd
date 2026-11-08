package com.accesmed.backend.Records.Agente.Response;

import java.util.List;

/**
 * Prestación publicada de una especialidad, con los médicos que la atienden hoy (relación
 * {@code MedicoPrestacion} vigente). No todos los médicos de una especialidad hacen todas sus
 * prestaciones.
 *
 * @param nombre {@code String} nombre de la prestación
 * @param requiereValidacion {@code boolean} {@code true} si alguna indicación vigente requiere validación: el turno arranca en {@code ESPERA_VALIDACION}
 * @param medicos {@code List<ListMedicoAgenteResponse>} médicos activos que atienden la prestación
 */
public record ListPrestacionAgenteResponse(
        String nombre,
        boolean requiereValidacion,
        List<ListMedicoAgenteResponse> medicos
) {
}
