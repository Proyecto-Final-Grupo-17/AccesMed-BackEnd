package com.accesmed.backend.Records.Paciente.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Record para la creación de un paciente nuevo, junto con las coberturas de obra social
 * existentes que declara (alta atómica: paciente + coberturas obra social-paciente en la
 * misma transacción).
 */
public record CreatePacienteRequest(

        /**
         * DNI del paciente ({@code String}, único). Máximo 15 caracteres.
         */
        @NotBlank(message = "El DNI es obligatorio.")
        @Size(max = 15, message = "El DNI no puede exceder 15 caracteres.")
        @Pattern(regexp = "\\d{7,9}", message = "El DNI debe tener entre 7 y 9 dígitos, sin puntos ni letras.")
        String dni,

        /**
         * Nombre del paciente ({@code String}). Máximo 100 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre,

        /**
         * Apellido del paciente ({@code String}). Máximo 100 caracteres.
         */
        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres.")
        String apellido,

        /**
         * Fecha de nacimiento del paciente ({@code LocalDate}). Debe ser pasada.
         */
        @NotNull(message = "La fecha de nacimiento es obligatoria.")
        @Past(message = "La fecha de nacimiento debe ser pasada.")
        LocalDate fechaNacimiento,

        /**
         * Email del paciente ({@code String}, único). Máximo 150 caracteres.
         */
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Número de teléfono del paciente ({@code String}, único). Identifica al paciente
         * en el canal chatbot. Máximo 30 caracteres.
         */
        @NotBlank(message = "El número de teléfono es obligatorio.")
        @Size(max = 30, message = "El número de teléfono no puede exceder 30 caracteres.")
        @Pattern(regexp = "\\+?\\d[\\d ()-]{5,28}\\d", message = "El número de teléfono solo puede tener dígitos, espacios, guiones, paréntesis y un + inicial.")
        String numeroTelefono,

        /**
         * Coberturas de obra social que declara el paciente ({@code List<AsignarObraSocialAnidadaRequest>}).
         * Puede venir vacía.
         */
        @Valid
        List<AsignarObraSocialAnidadaRequest> obrasSociales

) {
}
