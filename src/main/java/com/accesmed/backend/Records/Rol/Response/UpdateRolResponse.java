package com.accesmed.backend.Records.Rol.Response;

import com.accesmed.backend.Domain.Permiso;

import java.util.Set;
import java.util.UUID;

/**
 * Response de actualización de un rol.
 *
 * @param id {@code UUID} identificador del rol actualizado
 * @param nombre {@code String} nombre del rol
 * @param permisos {@code Set<Permiso>} permisos del rol
 */
public record UpdateRolResponse(
        UUID id,
        String nombre,
        Set<Permiso> permisos
) {
}
