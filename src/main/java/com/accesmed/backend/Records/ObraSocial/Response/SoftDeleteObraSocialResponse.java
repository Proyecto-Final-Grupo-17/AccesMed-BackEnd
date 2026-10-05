package com.accesmed.backend.Records.ObraSocial.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de una obra social.
 */
public record SoftDeleteObraSocialResponse(

        /**
         * Identificador único de la obra social dada de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja la obra social ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
