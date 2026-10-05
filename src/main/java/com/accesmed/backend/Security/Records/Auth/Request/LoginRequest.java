package com.accesmed.backend.Security.Records.Auth.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Record para autenticación: mail y contraseña.
 */
public record LoginRequest(

        /**
         * Mail del usuario ({@code String}, formato email). Obligatorio.
         */
        @NotBlank(message = "El mail es obligatorio.")
        @Email(message = "El mail debe ser válido.")
        String mail,

        /**
         * Contraseña ({@code String}). Obligatoria.
         */
        @NotBlank(message = "La contraseña es obligatoria.")
        String password

) {
}
