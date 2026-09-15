package com.accesmed.backend.Security.Records.Auth.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Record para confirmar un cambio de mail: el token mandado a la casilla nueva.
 */
public record ConfirmarCambioMailRequest(

        /**
         * Token de confirmación ({@code String}). Obligatorio.
         */
        @NotBlank(message = "El token es obligatorio.")
        String token

) {
}
