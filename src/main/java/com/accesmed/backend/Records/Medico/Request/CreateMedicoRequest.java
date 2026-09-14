package com.accesmed.backend.Records.Medico.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Record para la creación de un médico nuevo, junto con las prestaciones existentes que
 * atiende (alta atómica: médico + asignaciones médico-prestación en la misma transacción).
 */
public record CreateMedicoRequest(

        /**
         * Matrícula del médico ({@code String}, única). Máximo 30 caracteres.
         */
        @NotBlank(message = "La matrícula es obligatoria.")
        @Size(max = 30, message = "La matrícula no puede exceder 30 caracteres.")
        String matricula,

        /**
         * DNI del médico ({@code String}, único). Máximo 15 caracteres.
         */
        @NotBlank(message = "El DNI es obligatorio.")
        @Size(max = 15, message = "El DNI no puede exceder 15 caracteres.")
        String dni,

        /**
         * Nombre del médico ({@code String}). Máximo 100 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre,

        /**
         * Apellido del médico ({@code String}). Máximo 100 caracteres.
         */
        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres.")
        String apellido,

        /**
         * Email del médico ({@code String}, único). Máximo 150 caracteres.
         */
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Número de teléfono del médico ({@code String}). Máximo 30 caracteres.
         */
        @NotBlank(message = "El número de teléfono es obligatorio.")
        @Size(max = 30, message = "El número de teléfono no puede exceder 30 caracteres.")
        String numeroTelefono,

        /**
         * Identificador de la especialidad del médico ({@code UUID}).
         */
        @NotNull(message = "La especialidad es obligatoria.")
        UUID especialidadId,

        /**
         * Prestaciones existentes que atiende el médico ({@code List<AsignarPrestacionAnidadaRequest>}).
         * Puede venir vacía.
         */
        @Valid
        List<AsignarPrestacionAnidadaRequest> prestaciones,

        /**
         * Si se debe crear un usuario de acceso para el médico ({@code Boolean}, nullable).
         * Si es {@code true}, además de crear el médico se le crea el usuario de login
         * (el email será {@code Medico.email}). Si es {@code null} o {@code false}, el médico
         * se crea sin usuario. Requiere el permiso {@code USER_ALTA} para quien realiza la operación.
         */
        Boolean crearUsuario

) {
}
