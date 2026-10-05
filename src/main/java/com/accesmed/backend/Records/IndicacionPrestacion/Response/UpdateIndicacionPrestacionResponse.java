package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para la actualización de una indicación de prestación existente.
 * Contiene los mismos campos que {@code CreateIndicacionPrestacionResponse}.
 */
public record UpdateIndicacionPrestacionResponse(

        /**
         * Identificador único de la indicación ({@code UUID}).
         */
        UUID id,

        /**
         * Nombre de la indicación ({@code String}).
         */
        String nombre,

        /**
         * Descripción detallada de la indicación ({@code String}).
         */
        String descripcion,

        /**
         * Indica si esta indicación requiere validación ({@code Boolean}).
         */
        Boolean requiereValidacion,

        /**
         * Identificador de la prestación a la cual pertenece esta indicación ({@code UUID}).
         */
        UUID prestacionId,

        /**
         * Identificador del tipo de indicación ({@code UUID}).
         */
        UUID tipoIndicacionPrestacionId,

        /**
         * Nombre del tipo de indicación ({@code String}).
         */
        String tipoIndicacionPrestacionNombre

) {
}
