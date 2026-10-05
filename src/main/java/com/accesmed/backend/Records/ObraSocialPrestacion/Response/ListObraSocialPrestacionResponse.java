package com.accesmed.backend.Records.ObraSocialPrestacion.Response;

import com.accesmed.backend.Domain.ModalidadCobertura;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record de respuesta para el listado de coberturas obra social-prestación.
 */
public record ListObraSocialPrestacionResponse(

        /**
         * Identificador único de la cobertura ({@code UUID}).
         */
        UUID id,

        /**
         * Identificador del plan ({@code UUID}).
         */
        UUID planId,

        /**
         * Código del plan ({@code String}).
         */
        String planCodigo,

        /**
         * Nombre del plan ({@code String}).
         */
        String planNombre,

        /**
         * Identificador de la obra social del plan ({@code UUID}).
         */
        UUID obraSocialId,

        /**
         * Identificador de la prestación ({@code UUID}).
         */
        UUID prestacionId,

        /**
         * Código de la prestación ({@code String}).
         */
        String prestacionCodigo,

        /**
         * Nombre de la prestación ({@code String}).
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
        BigDecimal coseguro,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
