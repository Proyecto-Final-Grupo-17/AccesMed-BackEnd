package com.accesmed.backend.Records.Prestacion.Response;

import com.accesmed.backend.Domain.EstadoPrestacion;

import java.util.UUID;

/**
 * Record de respuesta para una transición de estado de prestación (publicar,
 * despublicar, deshabilitar).
 */
public record CambioEstadoPrestacionResponse(

        /**
         * Identificador único de la prestación ({@code UUID}).
         */
        UUID id,

        /**
         * Código único de la prestación ({@code String}).
         */
        String codigo,

        /**
         * Nombre de la prestación ({@code String}).
         */
        String nombre,

        /**
         * Estado actual de la prestación tras la transición ({@code EstadoPrestacion}).
         */
        EstadoPrestacion estadoActual

) {
}
