package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para el listado de indicaciones de prestación.
 * Versión plana optimizada para listar múltiples indicaciones sin la descripción.
 */
public record ListIndicacionPrestacionResponse(

        /**
         * Identificador único de la indicación ({@code UUID}).
         */
        UUID id,

        /**
         * Nombre de la indicación ({@code String}).
         */
        String nombre,

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
