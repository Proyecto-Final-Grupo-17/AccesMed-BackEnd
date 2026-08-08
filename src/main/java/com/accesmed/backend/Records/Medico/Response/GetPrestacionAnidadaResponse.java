package com.accesmed.backend.Records.Medico.Response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record de prestación anidada dentro de las respuestas de médico.
 */
public record GetPrestacionAnidadaResponse(

        /**
         * Identificador único de la asignación médico-prestación ({@code UUID}).
         */
        UUID id,

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
         * Indica si el médico atiende esta prestación de forma particular ({@code Boolean}).
         */
        Boolean atiendeParticular,

        /**
         * Precio particular de la prestación para este médico ({@code BigDecimal}).
         */
        BigDecimal precioParticular

) {
}
