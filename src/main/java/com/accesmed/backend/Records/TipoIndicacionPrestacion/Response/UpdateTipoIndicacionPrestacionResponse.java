package com.accesmed.backend.Records.TipoIndicacionPrestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para la actualización de un tipo de indicación de prestación existente.
 * Contiene los mismos campos que {@code CreateTipoIndicacionPrestacionResponse}.
 */
public record UpdateTipoIndicacionPrestacionResponse(

        /**
         * Identificador único del tipo de indicación ({@code UUID}).
         */
        UUID id,

        /**
         * Código único del tipo de indicación ({@code String}).
         */
        String codigo,

        /**
         * Nombre descriptivo del tipo de indicación ({@code String}).
         */
        String nombre

) {
}
