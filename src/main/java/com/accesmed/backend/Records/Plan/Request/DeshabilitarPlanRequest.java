package com.accesmed.backend.Records.Plan.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para deshabilitar un plan (transición terminal e irreversible).
 * El {@code motivo} es opcional.
 */
public record DeshabilitarPlanRequest(

        /**
         * Identificador del plan a deshabilitar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Motivo de la deshabilitación ({@code String}), opcional. Máximo 500 caracteres.
         */
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres.")
        String motivo

) {
}
