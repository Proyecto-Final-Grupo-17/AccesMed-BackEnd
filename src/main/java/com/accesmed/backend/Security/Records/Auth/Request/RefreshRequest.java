package com.accesmed.backend.Security.Records.Auth.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Record para refrescar el access token: el refresh token vigente.
 */
public record RefreshRequest(

        /**
         * Refresh token ({@code String}). Obligatorio.
         */
        @NotBlank(message = "El refresh token es obligatorio.")
        String refreshToken

) {
}
