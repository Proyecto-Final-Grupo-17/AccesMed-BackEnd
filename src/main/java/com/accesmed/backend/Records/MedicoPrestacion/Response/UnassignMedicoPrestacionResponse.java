package com.accesmed.backend.Records.MedicoPrestacion.Response;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Record de respuesta para el cierre de vigencia de una asignación médico-prestación.
 */
public record UnassignMedicoPrestacionResponse(

        /**
         * Identificador único de la asignación cerrada ({@code UUID}).
         */
        UUID id,

        /**
         * Fecha de fin de vigencia con la que quedó la asignación ({@code ZonedDateTime}).
         */
        ZonedDateTime fechaFinVigencia

) {
}
