package com.accesmed.backend.Records.Medico.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de un médico.
 */
public record SoftDeleteMedicoResponse(

        /**
         * Identificador único del médico dado de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja el médico ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
