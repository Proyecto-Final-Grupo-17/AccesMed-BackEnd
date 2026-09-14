package com.accesmed.backend.Records.Rol.Request;

import com.accesmed.backend.Domain.Permiso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Request para actualizar un rol existente. El {@code id} debe coincidir con el de la ruta.
 *
 * @param id {@code UUID} identificador del rol, requerido y debe coincidir con la ruta
 * @param nombre {@code String} nuevo nombre del rol, requerido
 * @param permisos {@code Set<Permiso>} nuevo conjunto de permisos, no vacío
 */
public record UpdateRolRequest(
        @NotNull(message = "El id del rol es requerido")
        UUID id,

        @NotBlank(message = "El nombre del rol es requerido")
        @Size(max = 60, message = "El nombre del rol no puede exceder 60 caracteres")
        String nombre,

        @NotEmpty(message = "El rol debe tener al menos un permiso")
        Set<Permiso> permisos
) {
}
