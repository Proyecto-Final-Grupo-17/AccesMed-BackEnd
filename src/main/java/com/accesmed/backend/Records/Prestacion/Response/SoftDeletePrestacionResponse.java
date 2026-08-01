package com.accesmed.backend.Records.Prestacion.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de una prestación.
 */
public record SoftDeletePrestacionResponse(

        /**
         * Identificador único de la prestación dada de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja la prestación ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
