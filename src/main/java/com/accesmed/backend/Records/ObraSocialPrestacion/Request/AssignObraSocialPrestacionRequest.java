package com.accesmed.backend.Records.ObraSocialPrestacion.Request;

import com.accesmed.backend.Domain.ModalidadCobertura;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record para asignar una prestación existente a un plan existente, con sus condiciones
 * de cobertura.
 */
public record AssignObraSocialPrestacionRequest(

        /**
         * Identificador del plan existente ({@code UUID}) al que se le asigna la prestación.
         */
        @NotNull(message = "El plan es obligatorio.")
        UUID planId,

        /**
         * Identificador de la prestación existente ({@code UUID}) a cubrir.
         */
        @NotNull(message = "La prestación es obligatoria.")
        UUID prestacionId,

        /**
         * Modalidad de cobertura ({@code ModalidadCobertura}).
         */
        @NotNull(message = "La modalidad de cobertura es obligatoria.")
        ModalidadCobertura modalidadCobertura,

        /**
         * Porcentaje de cobertura ({@code BigDecimal}, entre 0 y 100).
         */
        @NotNull(message = "El porcentaje de cobertura es obligatorio.")
        @DecimalMin(value = "0", message = "El porcentaje de cobertura no puede ser negativo.")
        @DecimalMax(value = "100", message = "El porcentaje de cobertura no puede superar 100.")
        BigDecimal porcentajeCobertura,

        /**
         * Coseguro fijo a cargo del paciente ({@code BigDecimal}, no negativo).
         */
        @NotNull(message = "El coseguro es obligatorio.")
        @PositiveOrZero(message = "El coseguro no puede ser negativo.")
        BigDecimal coseguro

) {
}
