package com.accesmed.backend.Records.Especialidad.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record para la creación de una especialidad nueva.
 */
public record CreateEspecialidadRequest(

        /**
         * Código único de la especialidad ({@code String}). Máximo 20 caracteres.
         */
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre descriptivo de la especialidad ({@code String}). Máximo 100 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre

) {
}
