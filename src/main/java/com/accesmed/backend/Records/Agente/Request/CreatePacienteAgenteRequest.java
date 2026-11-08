package com.accesmed.backend.Records.Agente.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Record para que el agente registre un paciente nuevo (ECU-PAC-1). El teléfono no viaja en el
 * body: sale del header {@code X-Paciente-Telefono}, verificado por el canal.
 */
public record CreatePacienteAgenteRequest(

        /**
         * DNI del paciente ({@code String}, único entre pacientes activos).
         */
        @NotBlank(message = "El DNI es obligatorio.")
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
         * Fecha de nacimiento del paciente ({@code LocalDate}). Debe ser pasada. Es obligatoria
         * porque la entidad {@code Paciente} no admite fecha de nacimiento nula.
         */
        @NotNull(message = "La fecha de nacimiento es obligatoria.")
        @Past(message = "La fecha de nacimiento debe ser pasada.")
        LocalDate fechaNacimiento,

        /**
         * Email del paciente ({@code String}, único). Es obligatorio porque la entidad
         * {@code Paciente} no admite email nulo.
         */
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Cobertura de obra social del paciente ({@code ObraSocialAgenteRequest}). Opcional.
         */
        @Valid
        ObraSocialAgenteRequest obraSocial

) {
}
