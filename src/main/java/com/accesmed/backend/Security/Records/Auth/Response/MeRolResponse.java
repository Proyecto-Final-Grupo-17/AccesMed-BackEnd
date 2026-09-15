package com.accesmed.backend.Security.Records.Auth.Response;

import com.accesmed.backend.Domain.Permiso;

import java.util.Set;
import java.util.UUID;

/**
 * Rol vigente anidado en {@link MeResponse}.
 *
 * @param id {@code UUID} identificador del rol
 * @param nombre {@code String} nombre del rol
 * @param permisos {@code Set<Permiso>} permisos del rol
 */
public record MeRolResponse(
        UUID id,
        String nombre,
        Set<Permiso> permisos
) {
}
