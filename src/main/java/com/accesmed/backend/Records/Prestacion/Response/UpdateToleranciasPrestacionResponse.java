package com.accesmed.backend.Records.Prestacion.Response;

import java.util.UUID;

/**
 * Record de respuesta para la actualización de las duraciones y tolerancias
 * de una prestación.
 */
public record UpdateToleranciasPrestacionResponse(

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
         * Duración mínima del turno en minutos ({@code Integer}).
         */
        Integer duracionMinimaMinutos,

        /**
         * Duración máxima del turno en minutos ({@code Integer}).
         */
        Integer duracionMaximaMinutos,

        /**
         * Tolerancia en minutos para solicitud de turno ({@code Integer}).
         */
        Integer tiempoToleranciaSolicitudMinutos,

        /**
         * Tolerancia en minutos para validación de turno ({@code Integer}).
         */
        Integer tiempoToleranciaValidacionMinutos,

        /**
         * Tolerancia en minutos para reprogramación de turno ({@code Integer}).
         */
        Integer tiempoToleranciaReprogramacionMinutos,

        /**
         * Tolerancia en minutos para confirmación de turno ({@code Integer}).
         */
        Integer tiempoToleranciaConfirmacionMinutos,

        /**
         * Tolerancia en minutos para cancelación de turno ({@code Integer}).
         */
        Integer tiempoToleranciaCancelacionMinutos,

        /**
         * Tolerancia en minutos para anuncio de turno ({@code Integer}).
         */
        Integer tiempoToleranciaAnuncioMinutos,

        /**
         * Tiempo de recordatorio en minutos para confirmación ({@code Integer}).
         */
        Integer tiempoRecordatorioConfirmacionMinutos,

        /**
         * Indicador de si la prestación está habilitada ({@code Boolean}).
         */
        Boolean habilitada

) {
}
