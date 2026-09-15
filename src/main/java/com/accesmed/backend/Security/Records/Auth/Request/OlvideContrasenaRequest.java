package com.accesmed.backend.Security.Records.Auth.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Record para iniciación de recuperación de contraseña: el mail de la cuenta.
 */
public record OlvideContrasenaRequest(

        /**
         * Mail del usuario ({@code String}, formato email). Obligatorio.
         */
        @NotBlank(message = "El mail es obligatorio.")
        @Email(message = "El mail debe ser válido.")
        String mail

) {
}
