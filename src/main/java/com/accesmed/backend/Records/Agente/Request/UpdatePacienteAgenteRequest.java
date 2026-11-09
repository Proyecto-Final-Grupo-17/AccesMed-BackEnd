package com.accesmed.backend.Records.Agente.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Record para que el agente modifique los datos del paciente (ECU-PAC-2). El paciente se
 * identifica por el header {@code X-Paciente-Telefono}. Un campo en {@code null} significa "no
 * lo toques". El DNI y el teléfono no son modificables desde el canal. Para pasarse a particular
 * (quitar la obra social) se manda {@code particular = true}.
 */
public record UpdatePacienteAgenteRequest(

        /**
         * Nombre del paciente ({@code String}). {@code null} deja el nombre sin tocar.
         */
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre,

        /**
         * Apellido del paciente ({@code String}). {@code null} deja el apellido sin tocar.
         */
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres.")
        String apellido,

        /**
         * Fecha de nacimiento ({@code LocalDate}). {@code null} la deja sin tocar.
         */
        @Past(message = "La fecha de nacimiento debe ser pasada.")
        LocalDate fechaNacimiento,

        /**
         * Email del paciente ({@code String}, único). {@code null} lo deja sin tocar.
         */
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Nueva cobertura de obra social ({@code ObraSocialAgenteRequest}). Si viene, reemplaza las
         * coberturas activas del paciente; {@code null} las deja sin tocar.
         */
        @Valid
        ObraSocialAgenteRequest obraSocial,

        /**
         * Pasarse a particular ({@code Boolean}). {@code true} da de baja las coberturas activas del
         * paciente; {@code null} o {@code false} las dejan sin tocar. No se combina con {@code obraSocial}.
         */
        Boolean particular

) {
}
