package com.accesmed.backend.Records.ObraSocial.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record de plan anidado dentro de la creación de una obra social nueva.
 */
public record CreatePlanAnidadoRequest(

        /**
         * Código del plan ({@code String}, único dentro de la obra social). Máximo 20 caracteres.
         */
        @NotBlank(message = "El código del plan es obligatorio.")
        @Size(max = 20, message = "El código del plan no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre del plan ({@code String}). Máximo 150 caracteres.
         */
        @NotBlank(message = "El nombre del plan es obligatorio.")
        @Size(max = 150, message = "El nombre del plan no puede exceder 150 caracteres.")
        String nombre

) {
}
