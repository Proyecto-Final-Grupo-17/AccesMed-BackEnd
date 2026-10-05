package com.accesmed.backend.Records.ObraSocial.Response;

import java.util.List;
import java.util.UUID;

/**
 * Record de respuesta para la creación de una obra social nueva, con sus planes iniciales.
 */
public record CreateObraSocialResponse(

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
        String razonSocial,

        /**
         * Planes de la obra social ({@code List<GetPlanAnidadoResponse>}).
         */
        List<GetPlanAnidadoResponse> planes

) {
}
