package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Record de respuesta para la baja programada de una indicación de prestación.
 */
public record ScheduleBajaIndicacionPrestacionResponse(

        /**
         * Identificador único de la indicación retirada ({@code UUID}).
         */
        UUID id,

        /**
         * Fecha en la que la indicación deja de estar vigente ({@code ZonedDateTime}).
         */
        ZonedDateTime fechaFinVigencia

) {
}
