package com.accesmed.backend.Records.Medico.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para actualizar un médico existente. Un campo en {@code null} o ausente
 * significa "no lo toques": el {@code id} es el único obligatorio. Las prestaciones que
 * atiende no se actualizan acá: se gestionan por {@code MedicoPrestacion/Asignar} y
 * {@code /Desasignar}.
 */
public record UpdateMedicoRequest(

        /**
         * Identificador del médico a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Matrícula del médico ({@code String}, única). Máximo 30 caracteres.
         * {@code null} deja la matrícula sin tocar.
         */
        @Size(max = 30, message = "La matrícula no puede exceder 30 caracteres.")
        String matricula,

        /**
         * DNI del médico ({@code String}, único). Máximo 15 caracteres.
         * {@code null} deja el DNI sin tocar.
         */
        @Size(max = 15, message = "El DNI no puede exceder 15 caracteres.")
        String dni,

        /**
         * Nombre del médico ({@code String}). Máximo 100 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre,

        /**
         * Apellido del médico ({@code String}). Máximo 100 caracteres.
         * {@code null} deja el apellido sin tocar.
         */
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres.")
        String apellido,

        /**
         * Email del médico ({@code String}, único). Máximo 150 caracteres.
         * {@code null} deja el email sin tocar.
         */
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Número de teléfono del médico ({@code String}). Máximo 30 caracteres.
         * {@code null} deja el número de teléfono sin tocar.
         */
        @Size(max = 30, message = "El número de teléfono no puede exceder 30 caracteres.")
        String numeroTelefono,

        /**
         * Identificador de la especialidad del médico ({@code UUID}).
         * {@code null} deja la especialidad sin tocar.
         */
        UUID especialidadId

) {
}
