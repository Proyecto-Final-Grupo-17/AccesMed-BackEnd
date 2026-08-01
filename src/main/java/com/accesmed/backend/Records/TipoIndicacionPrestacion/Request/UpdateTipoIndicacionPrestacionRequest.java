package com.accesmed.backend.Records.TipoIndicacionPrestacion.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para la actualización de un tipo de indicación de prestación existente.
 * El campo {@code id} viaja en el request además de la ruta;
 * el {@code codigo} es inmutable después del alta.
 */
public record UpdateTipoIndicacionPrestacionRequest(

        /**
         * Identificador del tipo a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

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
