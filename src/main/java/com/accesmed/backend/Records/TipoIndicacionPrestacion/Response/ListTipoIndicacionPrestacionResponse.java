package com.accesmed.backend.Records.TipoIndicacionPrestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para el listado de tipos de indicación de prestación.
 * Versión plana optimizada para listar múltiples tipos.
 */
public record ListTipoIndicacionPrestacionResponse(

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
