package com.accesmed.backend.Records.Rol.Response;

import com.accesmed.backend.Domain.Permiso;

import java.util.Set;
import java.util.UUID;

/**
 * Response de creación de un rol.
 *
 * @param id {@code UUID} identificador del rol creado
 * @param nombre {@code String} nombre del rol
 * @param permisos {@code Set<Permiso>} permisos del rol
 */
public record CreateRolResponse(
        UUID id,
        String nombre,
        Set<Permiso> permisos
) {
}
