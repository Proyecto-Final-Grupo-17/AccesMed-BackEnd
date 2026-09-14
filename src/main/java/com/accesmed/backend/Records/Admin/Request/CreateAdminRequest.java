package com.accesmed.backend.Records.Admin.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para crear un admin nuevo. La creación de un admin es atómica: también
 * crea el usuario de acceso, vinculando el email del admin.
 *
 * @param nombre {@code String} nombre del admin, requerido
 * @param apellido {@code String} apellido del admin, requerido
 * @param dni {@code String} DNI del admin, requerido y único entre los activos
 * @param email {@code String} email del admin, requerido, válido y único entre los activos
 */
public record CreateAdminRequest(
        @NotBlank(message = "El nombre del admin es requerido")
        @Size(max = 100, message = "El nombre del admin no puede exceder 100 caracteres")
        String nombre,

        @NotBlank(message = "El apellido del admin es requerido")
        @Size(max = 100, message = "El apellido del admin no puede exceder 100 caracteres")
        String apellido,

        @NotBlank(message = "El DNI del admin es requerido")
        @Size(max = 15, message = "El DNI del admin no puede exceder 15 caracteres")
        String dni,

        @NotBlank(message = "El email del admin es requerido")
        @Email(message = "El email del admin debe ser válido")
        @Size(max = 150, message = "El email del admin no puede exceder 150 caracteres")
        String email
) {
}
