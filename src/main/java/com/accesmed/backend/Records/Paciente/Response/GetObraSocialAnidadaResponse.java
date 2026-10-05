package com.accesmed.backend.Records.Paciente.Response;

import java.util.UUID;

/**
 * Record de cobertura de obra social anidada dentro de las respuestas de paciente.
 */
public record GetObraSocialAnidadaResponse(

        /**
         * Identificador único de la cobertura ({@code UUID}).
         */
        UUID id,

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
