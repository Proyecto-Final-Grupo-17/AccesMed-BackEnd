package com.accesmed.backend.Records.Medico.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record de prestación anidada dentro de la creación de un médico nuevo.
 */
public record AsignarPrestacionAnidadaRequest(

        /**
         * Identificador de la prestación existente a asignar ({@code UUID}).
         */
        @NotNull(message = "La prestación es obligatoria.")
        UUID prestacionId,

        /**
         * Indica si el médico atiende esta prestación de forma particular ({@code Boolean}).
         */
        @NotNull(message = "Atiende particular es obligatorio.")
        Boolean atiendeParticular,

        /**
         * Precio particular de la prestación para este médico ({@code BigDecimal}).
         */
        @NotNull(message = "El precio particular es obligatorio.")
        @Positive(message = "El precio particular debe ser mayor a cero.")
        BigDecimal precioParticular

) {
}
