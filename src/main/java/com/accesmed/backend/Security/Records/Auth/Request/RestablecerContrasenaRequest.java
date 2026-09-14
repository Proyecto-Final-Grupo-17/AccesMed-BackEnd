package com.accesmed.backend.Security.Records.Auth.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record para restablecimiento de contraseña: token de validación y contraseña nueva.
 */
public record RestablecerContrasenaRequest(

        /**
         * Token de validación ({@code String}). Obligatorio.
         */
        @NotBlank(message = "El token es obligatorio.")
        String token,

        /**
         * Contraseña nueva ({@code String}). Entre 8 y 100 caracteres.
         */
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres.")
        String passwordNueva

) {
}
