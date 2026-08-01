package com.accesmed.backend.Records.Prestacion.Response;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Record de respuesta para la habilitación de una prestación (cambio de estado de borrador a habilitada).
 * Contiene los datos esenciales de la prestación habilitada.
 */
public record EnablePrestacionResponse(

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
         * Fecha y hora de habilitación ({@code ZonedDateTime}).
         */
        ZonedDateTime fechaHabilitacion,

        /**
         * Indicador de si la prestación está habilitada ({@code Boolean}). Siempre {@code true} en este response.
         */
        Boolean habilitada

) {
}
