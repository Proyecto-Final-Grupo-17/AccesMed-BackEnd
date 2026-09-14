package com.accesmed.backend.Records.ObraSocialPrestacion.Response;

import com.accesmed.backend.Domain.ModalidadCobertura;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record de respuesta para la actualización de la cobertura de una asignación
 * plan-prestación.
 */
public record UpdateObraSocialPrestacionResponse(

        /**
         * Identificador único de la cobertura ({@code UUID}).
         */
        UUID id,

        /**
         * Identificador del plan ({@code UUID}).
         */
        UUID planId,

        /**
         * Identificador de la prestación ({@code UUID}).
         */
        UUID prestacionId,

        /**
         * Modalidad de cobertura ({@code ModalidadCobertura}).
         */
        ModalidadCobertura modalidadCobertura,

        /**
         * Porcentaje de cobertura ({@code BigDecimal}).
         */
        BigDecimal porcentajeCobertura,

        /**
         * Coseguro fijo a cargo del paciente ({@code BigDecimal}).
         */
        BigDecimal coseguro

) {
}
