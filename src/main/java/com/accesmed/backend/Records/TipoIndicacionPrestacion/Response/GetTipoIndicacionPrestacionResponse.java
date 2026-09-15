package com.accesmed.backend.Records.TipoIndicacionPrestacion.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.UUID;

/**
 * Record de respuesta para la obtención de un tipo de indicación de prestación por identificador.
 * Incluye todos los datos del tipo.
 */
public record GetTipoIndicacionPrestacionResponse(

        /**
         * Identificador único del tipo de indicación ({@code UUID}).
         */
        UUID id,

        /**
         * Código único del tipo de indicación ({@code String}).
         */
        String codigo,

        /**
         * Nombre descriptivo del tipo de indicación ({@code String}).
         */
        String nombre,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
