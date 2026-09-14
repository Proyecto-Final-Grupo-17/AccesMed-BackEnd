package com.accesmed.backend.Records.ObraSocial.Response;

import java.util.UUID;

/**
 * Record de respuesta para el listado de obras sociales.
 */
public record ListObraSocialResponse(

        /**
         * Identificador único de la obra social ({@code UUID}).
         */
        UUID id,

        /**
         * Código único de la obra social ({@code String}).
         */
        String codigo,

        /**
         * Nombre de la obra social ({@code String}).
         */
        String nombre,

        /**
         * Razón social de la obra social ({@code String}).
         */
        String razonSocial

) {
}
