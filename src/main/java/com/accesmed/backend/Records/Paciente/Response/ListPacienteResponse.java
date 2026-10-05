package com.accesmed.backend.Records.Paciente.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record de respuesta para el listado de pacientes.
 */
public record ListPacienteResponse(

        /**
         * Identificador único del paciente ({@code UUID}).
         */
        UUID id,

        /**
         * DNI del paciente ({@code String}).
         */
        String dni,

        /**
         * Nombre del paciente ({@code String}).
         */
        String nombre,

        /**
         * Apellido del paciente ({@code String}).
         */
        String apellido,

        /**
         * Fecha de nacimiento del paciente ({@code LocalDate}).
         */
        LocalDate fechaNacimiento,

        /**
         * Email del paciente ({@code String}).
         */
        String email,

        /**
         * Número de teléfono del paciente ({@code String}).
         */
        String numeroTelefono,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
