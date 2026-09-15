package com.accesmed.backend.Records.Rol.Request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para asignar un rol a un usuario.
 *
 * @param usuarioId {@code UUID} identificador del usuario al que se le asigna el rol
 */
public record AsignarRolRequest(
        @NotNull(message = "El id del usuario es requerido")
        UUID usuarioId
) {
}
