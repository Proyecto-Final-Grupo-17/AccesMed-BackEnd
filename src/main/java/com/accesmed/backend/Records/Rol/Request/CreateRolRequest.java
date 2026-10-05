package com.accesmed.backend.Records.Rol.Request;

import com.accesmed.backend.Domain.Permiso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request para crear un rol nuevo con su conjunto de permisos.
 *
 * @param nombre {@code String} nombre del rol, requerido y único entre los activos
 * @param permisos {@code Set<Permiso>} conjunto de permisos del rol, no vacío
 */
public record CreateRolRequest(
        @NotBlank(message = "El nombre del rol es requerido")
        @Size(max = 60, message = "El nombre del rol no puede exceder 60 caracteres")
        String nombre,

        @NotEmpty(message = "El rol debe tener al menos un permiso")
        Set<Permiso> permisos
) {
}
