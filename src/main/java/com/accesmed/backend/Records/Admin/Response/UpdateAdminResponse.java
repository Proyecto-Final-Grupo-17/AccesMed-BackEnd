package com.accesmed.backend.Records.Admin.Response;

import java.util.UUID;

/**
 * Response de actualización de un admin.
 *
 * @param id {@code UUID} identificador del admin actualizado
 * @param nombre {@code String} nombre del admin
 * @param apellido {@code String} apellido del admin
 * @param dni {@code String} DNI del admin
 * @param email {@code String} email del admin
 */
public record UpdateAdminResponse(
        UUID id,
        String nombre,
        String apellido,
        String dni,
        String email
) {
}
