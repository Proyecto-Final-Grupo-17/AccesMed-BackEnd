package com.accesmed.backend.Records.Especialidad.Response;

import java.util.UUID;

/**
 * Record de respuesta para la creación de una especialidad nueva.
 */
public record CreateEspecialidadResponse(

        /**
         * Identificador único de la especialidad ({@code UUID}).
         */
        UUID id,

        /**
         * Código único de la especialidad ({@code String}).
         */
        String codigo,

        /**
         * Nombre de la especialidad ({@code String}).
         */
        String nombre

) {
}
