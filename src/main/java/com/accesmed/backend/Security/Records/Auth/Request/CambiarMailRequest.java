package com.accesmed.backend.Security.Records.Auth.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record de request para que el propio usuario autenticado inicie el cambio de su mail.
 * El mail no se aplica hasta que se confirma el link mandado a la casilla nueva.
 */
public record CambiarMailRequest(

        /**
         * Mail nuevo propuesto ({@code String}), pendiente de confirmación.
         */
        @NotBlank
        @Email
        @Size(max = 150)
        String mailNuevo

) {
}
