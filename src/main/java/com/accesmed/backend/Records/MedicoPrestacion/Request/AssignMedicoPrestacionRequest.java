package com.accesmed.backend.Records.MedicoPrestacion.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record para asignar una prestación existente a un médico existente, con sus
 * condiciones de atención particular.
 */
public record AssignMedicoPrestacionRequest(

        /**
         * Identificador del médico ({@code UUID}).
         */
        @NotNull(message = "El médico es obligatorio.")
        UUID medicoId,

        /**
         * Identificador de la prestación ({@code UUID}).
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
        BigDecimal precioParticular,

        /**
         * Fecha de inicio de vigencia de la asignación ({@code LocalDate}).
         * {@code null} u ausente equivale a "hoy".
         */
        LocalDate fechaInicioVigencia

) {
}
