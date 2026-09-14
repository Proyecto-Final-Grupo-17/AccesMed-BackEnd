package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para la creación de una indicación de prestación nueva.
 * Incluye todos los datos de la indicación creada con su id asignado.
 */
public record CreateIndicacionPrestacionResponse(

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
