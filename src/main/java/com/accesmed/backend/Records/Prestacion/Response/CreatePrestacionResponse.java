package com.accesmed.backend.Records.Prestacion.Response;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Record de respuesta para la creación de una prestación nueva.
 * Incluye todos los datos de la prestación creada (con id asignado),
 * las indicaciones asociadas, duraciones en minutos, y estado de habilitación.
 */
public record CreatePrestacionResponse(

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
        EstadoPrestacion estadoActual,

        /**
         * Lista de indicaciones activas asociadas a esta prestación ({@code List<GetIndicacionPrestacionResponse>}).
         */
        List<GetIndicacionPrestacionResponse> indicaciones

) {
}
