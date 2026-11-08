package com.accesmed.backend.Records.Agente.Response;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos del paciente identificado por el teléfono del canal, sin ids internos.
 *
 * @param nombre {@code String} nombre
 * @param apellido {@code String} apellido
 * @param dni {@code String} DNI
 * @param fechaNacimiento {@code LocalDate} fecha de nacimiento
 * @param email {@code String} email
 * @param obrasSociales {@code List<ObraSocialAgenteResponse>} coberturas activas
 */
public record GetPacienteAgenteResponse(
        String nombre,
        String apellido,
        String dni,
        LocalDate fechaNacimiento,
        String email,
        List<ObraSocialAgenteResponse> obrasSociales
) {
}
