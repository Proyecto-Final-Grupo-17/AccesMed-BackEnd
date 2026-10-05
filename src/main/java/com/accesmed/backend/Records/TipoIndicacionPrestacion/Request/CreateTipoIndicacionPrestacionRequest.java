package com.accesmed.backend.Records.TipoIndicacionPrestacion.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record para la creación de un tipo de indicación de prestación nuevo.
 * Incluye código único y nombre descriptivo.
 */
public record CreateTipoIndicacionPrestacionRequest(

        /**
         * Código único del tipo de indicación ({@code String}). Máximo 20 caracteres.
         */
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre descriptivo del tipo de indicación ({@code String}). Máximo 100 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre

) {
}
