package com.accesmed.backend.Records.ObraSocialPaciente.Response;

import java.util.UUID;

/**
 * Record de respuesta para la asignación de una cobertura de obra social a un paciente.
 */
public record GetObraSocialPacienteResponse(

        /**
         * Identificador único de la cobertura ({@code UUID}).
         */
        UUID id,

        /**
         * Identificador del paciente ({@code UUID}).
         */
        UUID pacienteId,

        /**
         * Identificador de la obra social ({@code UUID}).
         */
        UUID obraSocialId,

        /**
         * Nombre de la obra social ({@code String}).
         */
        String obraSocialNombre,

        /**
         * Identificador del plan ({@code UUID}).
         */
        UUID planId,

        /**
         * Nombre del plan ({@code String}).
         */
        String planNombre,

        /**
         * Número de socio del paciente en el plan ({@code String}).
         */
        String nroSocio

) {
}
