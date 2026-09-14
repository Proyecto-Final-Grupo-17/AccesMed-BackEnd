package com.accesmed.backend.Records.Plan.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para actualizar un plan existente. Un campo en {@code null} o ausente
 * significa "no lo toques": el {@code id} es el único obligatorio.
 */
public record UpdatePlanRequest(

        /**
         * Identificador del plan a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Código del plan ({@code String}). Máximo 20 caracteres.
         * {@code null} deja el código sin tocar.
         */
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre del plan ({@code String}). Máximo 150 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre

) {
}
