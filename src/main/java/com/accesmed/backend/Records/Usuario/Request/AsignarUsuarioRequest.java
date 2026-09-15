package com.accesmed.backend.Records.Usuario.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos para asignar o reasignar el usuario de acceso de un médico o un admin. El id
 * del médico/admin viaja en la ruta, no acá.
 *
 * @param mail {@code String} mail de login del nuevo usuario
 */
public record AsignarUsuarioRequest(
        @NotBlank @Email String mail
) {
}
