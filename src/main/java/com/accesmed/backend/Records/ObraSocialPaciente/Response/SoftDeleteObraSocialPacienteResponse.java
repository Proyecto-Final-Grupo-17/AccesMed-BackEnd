package com.accesmed.backend.Records.ObraSocialPaciente.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Record de respuesta para la baja lógica de una cobertura de obra social de un paciente.
 */
public record SoftDeleteObraSocialPacienteResponse(

        /**
         * Identificador único de la cobertura dada de baja ({@code UUID}).
         */
        UUID id,

        /**
         * Momento en que se dio de baja la cobertura ({@code Instant}).
         */
        Instant deletedAt,

        /**
         * Motivo de la baja ({@code String}).
         */
        String deletedReason

) {
}
