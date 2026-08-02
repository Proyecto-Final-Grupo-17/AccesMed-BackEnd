package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de una indicación de prestación.
 */
public record SoftDeleteIndicacionPrestacionResponse(

        /**
         * Identificador único de la indicación dada de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja la indicación ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
