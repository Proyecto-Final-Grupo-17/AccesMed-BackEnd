package com.accesmed.backend.Records.Admin.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request para actualizar un admin existente. Solo permite actualizar nombre y apellido.
 * El DNI y email son inmutables (se crean en la alta atómica y no se editan luego).
 * El {@code id} debe coincidir con el de la ruta.
 *
 * @param id {@code UUID} identificador del admin, requerido y debe coincidir con la ruta
 * @param nombre {@code String} nuevo nombre del admin, requerido
 * @param apellido {@code String} nuevo apellido del admin, requerido
 */
public record UpdateAdminRequest(
        @NotNull(message = "El id del admin es requerido")
        UUID id,

        @NotBlank(message = "El nombre del admin es requerido")
        @Size(max = 100, message = "El nombre del admin no puede exceder 100 caracteres")
        String nombre,

        @NotBlank(message = "El apellido del admin es requerido")
        @Size(max = 100, message = "El apellido del admin no puede exceder 100 caracteres")
        String apellido
) {
}
