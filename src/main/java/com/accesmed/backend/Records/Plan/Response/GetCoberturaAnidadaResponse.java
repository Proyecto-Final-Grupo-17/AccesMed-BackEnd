package com.accesmed.backend.Records.Plan.Response;

import com.accesmed.backend.Domain.ModalidadCobertura;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record de cobertura de prestación anidada dentro de las respuestas de plan.
 */
public record GetCoberturaAnidadaResponse(

        /**
         * Identificador único de la cobertura ({@code UUID}).
         */
        UUID id,

        /**
         * Identificador de la prestación cubierta ({@code UUID}).
         */
        UUID prestacionId,

        /**
         * Código de la prestación cubierta ({@code String}).
         */
        String prestacionCodigo,

        /**
         * Nombre de la prestación cubierta ({@code String}).
         */
        String prestacionNombre,

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
