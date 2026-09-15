package com.accesmed.backend.Security.Records.Auth.Response;

/**
 * Record de respuesta para refresco de access token: el token nuevo.
 */
public record RefreshResponse(

        /**
         * Access token JWT nuevo ({@code String}).
         */
        String accessToken

) {
}
