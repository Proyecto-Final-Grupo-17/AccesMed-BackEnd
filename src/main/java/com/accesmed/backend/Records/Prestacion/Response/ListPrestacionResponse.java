package com.accesmed.backend.Records.Prestacion.Response;

import java.time.ZonedDateTime;
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
         * Fecha y hora de habilitación ({@code ZonedDateTime}). Nulo si la prestación está en borrador.
         */
        ZonedDateTime fechaHabilitacion,

        /**
         * Indicador de si la prestación está habilitada ({@code Boolean}).
         * {@code true} si {@code fechaHabilitacion} no es nulo, {@code false} en caso contrario.
         */
        Boolean habilitada

) {
}
