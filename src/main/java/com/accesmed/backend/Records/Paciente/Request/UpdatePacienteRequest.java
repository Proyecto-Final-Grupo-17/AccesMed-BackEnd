package com.accesmed.backend.Records.Paciente.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record para actualizar un paciente existente. Un campo en {@code null} o ausente
 * significa "no lo toques": el {@code id} es el único obligatorio. Las coberturas de
 * obra social no se actualizan acá: se gestionan por {@code ObraSocialPaciente/Asignar}
 * y {@code /Desasignar}.
 */
public record UpdatePacienteRequest(

        /**
         * Identificador del paciente a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * DNI del paciente ({@code String}, único). Máximo 15 caracteres.
         * {@code null} deja el DNI sin tocar.
         */
        @Size(max = 15, message = "El DNI no puede exceder 15 caracteres.")
        String dni,

        /**
         * Nombre del paciente ({@code String}). Máximo 100 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre,

        /**
         * Apellido del paciente ({@code String}). Máximo 100 caracteres.
         * {@code null} deja el apellido sin tocar.
         */
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres.")
        String apellido,

        /**
         * Fecha de nacimiento del paciente ({@code LocalDate}). Debe ser pasada.
         * {@code null} deja la fecha de nacimiento sin tocar.
         */
        @Past(message = "La fecha de nacimiento debe ser pasada.")
        LocalDate fechaNacimiento,

        /**
         * Email del paciente ({@code String}, único). Máximo 150 caracteres.
         * {@code null} deja el email sin tocar.
         */
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Número de teléfono del paciente ({@code String}, único). Máximo 30 caracteres.
         * {@code null} deja el número de teléfono sin tocar.
         */
        @Size(max = 30, message = "El número de teléfono no puede exceder 30 caracteres.")
        String numeroTelefono

) {
}
