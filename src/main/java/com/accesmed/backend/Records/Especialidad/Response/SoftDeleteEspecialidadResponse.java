package com.accesmed.backend.Records.Especialidad.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de una especialidad.
 */
public record SoftDeleteEspecialidadResponse(

        /**
         * Identificador único de la especialidad dada de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja la especialidad ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
