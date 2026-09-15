package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.UUID;

/**
 * Record de respuesta para la obtención de una indicación de prestación por identificador.
 * Incluye todos los datos de la indicación.
 */
public record GetIndicacionPrestacionResponse(

        /**
         * Identificador único de la indicación ({@code UUID}).
         */
        UUID id,

        /**
         * Nombre de la indicación ({@code String}).
         */
        String nombre,

        /**
         * Descripción detallada de la indicación ({@code String}).
         */
        String descripcion,

        /**
         * Indica si esta indicación requiere validación ({@code Boolean}).
         */
        Boolean requiereValidacion,

        /**
         * Identificador de la prestación a la cual pertenece esta indicación ({@code UUID}).
         */
        UUID prestacionId,

        /**
         * Identificador del tipo de indicación ({@code UUID}).
         */
        UUID tipoIndicacionPrestacionId,

        /**
         * Nombre del tipo de indicación ({@code String}).
         */
        String tipoIndicacionPrestacionNombre,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
