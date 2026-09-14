package com.accesmed.backend.Records.Prestacion.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para la creación anidada de una indicación dentro de {@code CreatePrestacionRequest}.
 * No incluye {@code prestacionId} (lo aporta la prestación que se está creando).
 */
public record CreateIndicacionPrestacionAnidadaRequest(

        /**
         * Nombre de la indicación ({@code String}). Máximo 150 caracteres.
         */
        @NotBlank(message = "El nombre de la indicación es obligatorio.")
        @Size(max = 150, message = "El nombre de la indicación no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Descripción detallada de la indicación ({@code String}). Máximo 1000 caracteres.
         */
        @NotBlank(message = "La descripción de la indicación es obligatoria.")
        @Size(max = 1000, message = "La descripción de la indicación no puede exceder 1000 caracteres.")
        String descripcion,

        /**
         * Indica si esta indicación requiere validación ({@code Boolean}).
         */
        @NotNull(message = "El atributo 'requiereValidacion' es obligatorio.")
        Boolean requiereValidacion,

        /**
         * Identificador del tipo de indicación ({@code UUID}).
         */
        @NotNull(message = "El identificador del tipo de indicación es obligatorio.")
        UUID tipoIndicacionPrestacionId

) {
}
