package com.accesmed.backend.Records.MedicoPrestacion.Response;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Record de respuesta para la asignación de una prestación a un médico.
 */
public record GetMedicoPrestacionResponse(

        /**
         * Identificador único de la asignación ({@code UUID}).
         */
        UUID id,

        /**
         * Identificador del médico ({@code UUID}).
         */
        UUID medicoId,

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
        BigDecimal precioParticular,

        /**
         * Fecha de inicio de vigencia de la asignación ({@code ZonedDateTime}).
         */
        ZonedDateTime fechaInicioVigencia,

        /**
         * Fecha de fin de vigencia de la asignación ({@code ZonedDateTime}).
         * {@code null} significa vigente sin corte programado.
         */
        ZonedDateTime fechaFinVigencia

) {
}
