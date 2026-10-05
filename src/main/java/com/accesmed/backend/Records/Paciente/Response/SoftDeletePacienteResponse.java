package com.accesmed.backend.Records.Paciente.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de un paciente.
 */
public record SoftDeletePacienteResponse(

        /**
         * Identificador único del paciente dado de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja el paciente ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
