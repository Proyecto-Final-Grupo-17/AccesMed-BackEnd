package com.accesmed.backend.Records.Prestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para la actualización de los datos generales de una
 * prestación en borrador (nombre y especialidad).
 */
public record UpdatePrestacionNoHabilitadaResponse(

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
         * Indicador de si la prestación está habilitada ({@code Boolean}).
         * Siempre {@code false} en este response, ya que la operación exige borrador.
         */
        Boolean habilitada

) {
}
