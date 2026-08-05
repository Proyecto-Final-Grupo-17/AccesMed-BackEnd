package com.accesmed.backend.Records.Prestacion.Response;

import com.accesmed.backend.Domain.EstadoPrestacion;

import java.util.UUID;

/**
 * Record de respuesta para el listado de prestaciones.
 * Versión plana sin indicaciones ni duraciones detalladas,
 * optimizada para listar múltiples prestaciones de forma compacta.
 */
public record ListPrestacionResponse(

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
         * Identificador de la especialidad ({@code UUID}).
         */
        UUID especialidadId,

        /**
         * Nombre de la especialidad ({@code String}).
         */
        String especialidadNombre,

        /**
         * Estado actual de la prestación ({@code EstadoPrestacion}).
         */
        EstadoPrestacion estadoActual

) {
}
