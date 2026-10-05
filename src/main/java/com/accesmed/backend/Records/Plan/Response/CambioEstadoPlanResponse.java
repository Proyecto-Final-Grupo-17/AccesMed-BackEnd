package com.accesmed.backend.Records.Plan.Response;

import com.accesmed.backend.Domain.EstadoPlan;

import java.util.UUID;

/**
 * Record de respuesta para una transición de estado de plan (publicar, despublicar,
 * deshabilitar).
 */
public record CambioEstadoPlanResponse(

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
         * Estado actual del plan tras la transición ({@code EstadoPlan}).
         */
        EstadoPlan estadoActual

) {
}
