package com.accesmed.backend.Records.IndicacionPrestacion.Request;

import com.accesmed.backend.Records.Prestacion.Request.CreateIndicacionPrestacionAnidadaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Record para crear varias indicaciones de prestación juntas, en una sola operación,
 * todas asociadas a la misma prestación.
 */
public record CreateIndicacionesPrestacionRequest(

        /**
         * Identificador de la prestación a la que pertenecen todas las indicaciones ({@code UUID}).
         */
        @NotNull(message = "El identificador de prestación es obligatorio.")
        UUID prestacionId,

        /**
         * Indicaciones a crear ({@code List<CreateIndicacionPrestacionAnidadaRequest>}).
         * No puede estar vacía.
         */
        @NotEmpty(message = "Debe incluir al menos una indicación.")
        List<@Valid CreateIndicacionPrestacionAnidadaRequest> indicaciones

) {
}
