package com.accesmed.backend.Records.IndicacionPrestacion.Response;

import java.util.List;

/**
 * Record de respuesta para la creación de varias indicaciones de prestación juntas.
 */
public record CreateIndicacionesPrestacionResponse(

        /**
         * Indicaciones creadas ({@code List<CreateIndicacionPrestacionResponse>}).
         */
        List<CreateIndicacionPrestacionResponse> indicaciones

) {
}
