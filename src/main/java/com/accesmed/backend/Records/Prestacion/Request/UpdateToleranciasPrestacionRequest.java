package com.accesmed.backend.Records.Prestacion.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Record para actualizar las duraciones y tolerancias de una prestación.
 * Es el único grupo de campos editable una vez que la prestación está habilitada;
 * mientras está en borrador también se edita por acá. Un campo en {@code null} o
 * ausente significa "no lo toques": el {@code id} es el único obligatorio.
 */
public record UpdateToleranciasPrestacionRequest(

        /**
         * Identificador de la prestación a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Duración mínima del turno en minutos ({@code Integer}). Debe ser positivo.
         */
        @Positive(message = "La duración mínima debe ser mayor a cero.")
        Integer duracionMinimaMinutos,

        /**
         * Duración máxima del turno en minutos ({@code Integer}). Debe ser positivo.
         */
        @Positive(message = "La duración máxima debe ser mayor a cero.")
        Integer duracionMaximaMinutos,

        /**
         * Tolerancia en minutos para solicitud de turno ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de tolerancia de solicitud debe ser cero o positivo.")
        Integer tiempoToleranciaSolicitudMinutos,

        /**
         * Tolerancia en minutos para validación de turno ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de tolerancia de validación debe ser cero o positivo.")
        Integer tiempoToleranciaValidacionMinutos,

        /**
         * Tolerancia en minutos para reprogramación de turno ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de tolerancia de reprogramación debe ser cero o positivo.")
        Integer tiempoToleranciaReprogramacionMinutos,

        /**
         * Tolerancia en minutos para confirmación de turno ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de tolerancia de confirmación debe ser cero o positivo.")
        Integer tiempoToleranciaConfirmacionMinutos,

        /**
         * Tolerancia en minutos para cancelación de turno ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de tolerancia de cancelación debe ser cero o positivo.")
        Integer tiempoToleranciaCancelacionMinutos,

        /**
         * Tolerancia en minutos para anuncio de turno ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de tolerancia de anuncio debe ser cero o positivo.")
        Integer tiempoToleranciaAnuncioMinutos,

        /**
         * Tiempo de recordatorio en minutos para confirmación ({@code Integer}). Cero o positivo.
         */
        @PositiveOrZero(message = "El tiempo de recordatorio de confirmación debe ser cero o positivo.")
        Integer tiempoRecordatorioConfirmacionMinutos

) {
}
