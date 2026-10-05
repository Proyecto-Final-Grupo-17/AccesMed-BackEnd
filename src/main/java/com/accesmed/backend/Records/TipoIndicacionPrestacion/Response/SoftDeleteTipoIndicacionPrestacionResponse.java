package com.accesmed.backend.Records.TipoIndicacionPrestacion.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de un tipo de indicación de prestación.
 */
public record SoftDeleteTipoIndicacionPrestacionResponse(

        /**
         * Identificador único del tipo de indicación dado de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja el tipo de indicación ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
