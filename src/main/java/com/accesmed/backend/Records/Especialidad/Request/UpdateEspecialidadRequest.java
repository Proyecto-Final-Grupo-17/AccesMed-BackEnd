package com.accesmed.backend.Records.Especialidad.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para actualizar una especialidad existente. Un campo en {@code null} o ausente
 * significa "no lo toques": el {@code id} es el único obligatorio.
 */
public record UpdateEspecialidadRequest(

        /**
         * Identificador de la especialidad a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Código de la especialidad ({@code String}). Máximo 20 caracteres.
         * {@code null} deja el código sin tocar.
         */
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre descriptivo de la especialidad ({@code String}). Máximo 100 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String nombre

) {
}
