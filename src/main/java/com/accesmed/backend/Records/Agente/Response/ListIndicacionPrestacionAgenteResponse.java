package com.accesmed.backend.Records.Agente.Response;

/**
 * Indicación vigente de una prestación (preparación o documentación que el paciente tiene que
 * cumplir para el turno).
 *
 * @param nombre {@code String} nombre de la indicación (ej. "Ayuno de 8 horas", "Orden médica")
 * @param descripcion {@code String} detalle de la indicación
 * @param requiereValidacion {@code boolean} {@code true} si la clínica tiene que validarla antes del turno
 */
public record ListIndicacionPrestacionAgenteResponse(
        String nombre,
        String descripcion,
        boolean requiereValidacion
) {
}
