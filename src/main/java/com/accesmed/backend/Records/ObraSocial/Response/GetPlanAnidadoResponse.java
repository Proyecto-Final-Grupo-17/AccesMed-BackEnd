package com.accesmed.backend.Records.ObraSocial.Response;

import com.accesmed.backend.Domain.EstadoPlan;

import java.util.UUID;

/**
 * Record de plan anidado dentro de las respuestas de obra social.
 */
public record GetPlanAnidadoResponse(

        /**
         * Identificador único del plan ({@code UUID}).
         */
        UUID id,

        /**
         * Código del plan ({@code String}).
         */
        String codigo,

        /**
         * Nombre del plan ({@code String}).
         */
        String nombre,

        /**
         * Estado actual del plan ({@code EstadoPlan}).
         */
        EstadoPlan estadoActual

) {
}
