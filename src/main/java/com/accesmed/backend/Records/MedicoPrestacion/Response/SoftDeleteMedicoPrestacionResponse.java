package com.accesmed.backend.Records.MedicoPrestacion.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de una asignación médico-prestación.
 */
public record SoftDeleteMedicoPrestacionResponse(

        /**
         * Identificador único de la asignación dada de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja la asignación ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
