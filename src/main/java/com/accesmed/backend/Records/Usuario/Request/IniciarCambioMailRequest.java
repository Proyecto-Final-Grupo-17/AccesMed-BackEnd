package com.accesmed.backend.Records.Usuario.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record de request para que el SuperAdmin inicie el cambio de mail de un usuario. El id
 * del usuario viaja en la ruta, no en el body. El mail no se aplica hasta que el usuario
 * confirma el link mandado a la casilla nueva.
 */
public record IniciarCambioMailRequest(

        /**
         * Mail nuevo propuesto ({@code String}), pendiente de confirmación.
         */
        @NotBlank
        @Email
        @Size(max = 150)
        String mailNuevo

) {
}
