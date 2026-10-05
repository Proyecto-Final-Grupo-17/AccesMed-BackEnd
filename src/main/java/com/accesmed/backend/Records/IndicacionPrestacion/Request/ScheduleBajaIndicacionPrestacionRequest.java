package com.accesmed.backend.Records.IndicacionPrestacion.Request;

import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Record para programar la baja de una indicación de prestación existente, cerrando su
 * vigencia. El campo {@code id} viaja en el request además de la ruta.
 */
public record ScheduleBajaIndicacionPrestacionRequest(

        /**
         * Identificador de la indicación a retirar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Fecha en la que la indicación deja de estar vigente ({@code ZonedDateTime}).
         * Admite una fecha futura para programar el retiro. Si no se envía, se cierra la
         * vigencia en el momento de la solicitud.
         */
        ZonedDateTime fechaFinVigencia

) {
}
