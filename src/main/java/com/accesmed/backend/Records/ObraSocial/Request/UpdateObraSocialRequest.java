package com.accesmed.backend.Records.ObraSocial.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para actualizar una obra social existente. Un campo en {@code null} o ausente
 * significa "no lo toques": el {@code id} es el único obligatorio.
 */
public record UpdateObraSocialRequest(

        /**
         * Identificador de la obra social a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Código de la obra social ({@code String}). Máximo 20 caracteres.
         * {@code null} deja el código sin tocar.
         */
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre descriptivo de la obra social ({@code String}). Máximo 150 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Razón social de la obra social ({@code String}). Máximo 200 caracteres.
         * {@code null} deja la razón social sin tocar.
         */
        @Size(max = 200, message = "La razón social no puede exceder 200 caracteres.")
        String razonSocial

) {
}
