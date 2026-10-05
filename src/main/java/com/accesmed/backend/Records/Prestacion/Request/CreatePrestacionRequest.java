package com.accesmed.backend.Records.Prestacion.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Record para la creación de una prestación nueva.
 * Contiene el código, nombre, duraciones y tolerancias en minutos (como enteros),
 * identificador de especialidad, e indicaciones anidadas opcionales.
 * Los campos {@code id} y {@code fechaHabilitacion} no se incluyen aquí
 * (se generan al persistir).
 */
public record CreatePrestacionRequest(

        /**
         * Código único de la prestación ({@code String}, inmutable). Máximo 20 caracteres.
         */
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre descriptivo de la prestación ({@code String}). Máximo 150 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Duración mínima del turno en minutos ({@code Integer}). Debe ser positivo.
         */
        @NotNull(message = "La duración mínima es obligatoria.")
        @Positive(message = "La duración mínima debe ser mayor a cero.")
        Integer duracionMinimaMinutos,

        /**
         * Duración máxima del turno en minutos ({@code Integer}). Debe ser positivo.
         */
        @NotNull(message = "La duración máxima es obligatoria.")
        @Positive(message = "La duración máxima debe ser mayor a cero.")
        Integer duracionMaximaMinutos,

        /**
         * Tolerancia en minutos para solicitud de turno ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de tolerancia de solicitud es obligatorio.")
        @PositiveOrZero(message = "El tiempo de tolerancia de solicitud debe ser cero o positivo.")
        Integer tiempoToleranciaSolicitudMinutos,

        /**
         * Tolerancia en minutos para validación de turno ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de tolerancia de validación es obligatorio.")
        @PositiveOrZero(message = "El tiempo de tolerancia de validación debe ser cero o positivo.")
        Integer tiempoToleranciaValidacionMinutos,

        /**
         * Tolerancia en minutos para reprogramación de turno ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de tolerancia de reprogramación es obligatorio.")
        @PositiveOrZero(message = "El tiempo de tolerancia de reprogramación debe ser cero o positivo.")
        Integer tiempoToleranciaReprogramacionMinutos,

        /**
         * Tolerancia en minutos para confirmación de turno ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de tolerancia de confirmación es obligatorio.")
        @PositiveOrZero(message = "El tiempo de tolerancia de confirmación debe ser cero o positivo.")
        Integer tiempoToleranciaConfirmacionMinutos,

        /**
         * Tolerancia en minutos para cancelación de turno ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de tolerancia de cancelación es obligatorio.")
        @PositiveOrZero(message = "El tiempo de tolerancia de cancelación debe ser cero o positivo.")
        Integer tiempoToleranciaCancelacionMinutos,

        /**
         * Tolerancia en minutos para anuncio de turno ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de tolerancia de anuncio es obligatorio.")
        @PositiveOrZero(message = "El tiempo de tolerancia de anuncio debe ser cero o positivo.")
        Integer tiempoToleranciaAnuncioMinutos,

        /**
         * Tiempo de recordatorio en minutos para confirmación ({@code Integer}). Cero o positivo.
         */
        @NotNull(message = "El tiempo de recordatorio de confirmación es obligatorio.")
        @PositiveOrZero(message = "El tiempo de recordatorio de confirmación debe ser cero o positivo.")
        Integer tiempoRecordatorioConfirmacionMinutos,

        /**
         * Identificador de la especialidad ({@code UUID}) a la cual pertenece esta prestación.
         */
        @NotNull(message = "El identificador de especialidad es obligatorio.")
        UUID especialidadId,

        /**
         * Indicaciones anidadas opcionales ({@code List<CreateIndicacionPrestacionAnidadaRequest>}).
         * Si se proporciona, cada elemento se valida con {@link Valid}.
         */
        @Valid
        List<CreateIndicacionPrestacionAnidadaRequest> indicaciones

) {
}
