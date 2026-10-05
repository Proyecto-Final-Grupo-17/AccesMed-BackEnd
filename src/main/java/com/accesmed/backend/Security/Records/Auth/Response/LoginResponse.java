package com.accesmed.backend.Security.Records.Auth.Response;

/**
 * Record de respuesta para autenticación exitosa: access token y refresh token.
 */
public record LoginResponse(

        /**
         * Access token JWT ({@code String}).
         */
        String accessToken,

        /**
         * Refresh token para renovar el access token ({@code String}).
         */
        String refreshToken

) {
}
