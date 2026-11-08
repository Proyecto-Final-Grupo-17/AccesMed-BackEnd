package com.accesmed.backend.Records.Agente.Response;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos del paciente después de modificarlos desde el canal, sin ids internos.
 *
 * @param nombre {@code String} nombre
 * @param apellido {@code String} apellido
 * @param fechaNacimiento {@code LocalDate} fecha de nacimiento
 * @param email {@code String} email
 * @param obrasSociales {@code List<ObraSocialAgenteResponse>} coberturas activas
 */
public record UpdatePacienteAgenteResponse(
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        String email,
        List<ObraSocialAgenteResponse> obrasSociales
) {
}
