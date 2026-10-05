package com.accesmed.backend.Records.TipoIndicacionPrestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para la creación de un tipo de indicación de prestación nuevo.
 * Incluye el identificador asignado al tipo creado.
 */
public record CreateTipoIndicacionPrestacionResponse(

        /**
         * Identificador único del tipo de indicación ({@code UUID}).
         */
        UUID id,

        /**
         * Código único del tipo de indicación ({@code String}).
         */
        String codigo,

        /**
         * Nombre descriptivo del tipo de indicación ({@code String}).
         */
        String nombre

) {
}
