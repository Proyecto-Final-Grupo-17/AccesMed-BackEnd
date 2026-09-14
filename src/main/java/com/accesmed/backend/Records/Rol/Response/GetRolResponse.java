package com.accesmed.backend.Records.Rol.Response;

import com.accesmed.backend.Domain.Permiso;

import java.util.Set;
import java.util.UUID;

/**
 * Response de lectura de un rol. Incluye indicador de rol de sistema.
 *
 * @param id {@code UUID} identificador del rol
 * @param nombre {@code String} nombre del rol
 * @param esSistema {@code Boolean} indicador de si es un rol de sistema (no editable)
 * @param permisos {@code Set<Permiso>} permisos del rol
 */
public record GetRolResponse(
        UUID id,
        String nombre,
        Boolean esSistema,
        Set<Permiso> permisos
) {
}
