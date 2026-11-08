package com.accesmed.backend.Records.Agente.Response;

import java.util.List;

/**
 * Prestación publicada encontrada por parecido con el nombre, con su especialidad, sus
 * indicaciones vigentes y los médicos que la atienden.
 *
 * @param nombre {@code String} nombre de la prestación
 * @param especialidad {@code String} nombre de su especialidad
 * @param requiereValidacion {@code boolean} {@code true} si alguna indicación vigente requiere validación: el turno arranca en {@code ESPERA_VALIDACION}
 * @param indicaciones {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones vigentes
 * @param medicos {@code List<ListMedicoAgenteResponse>} médicos activos que atienden la prestación
 */
public record SearchPrestacionAgenteResponse(
        String nombre,
        String especialidad,
        boolean requiereValidacion,
        List<ListIndicacionPrestacionAgenteResponse> indicaciones,
        List<ListMedicoAgenteResponse> medicos
) {
}
