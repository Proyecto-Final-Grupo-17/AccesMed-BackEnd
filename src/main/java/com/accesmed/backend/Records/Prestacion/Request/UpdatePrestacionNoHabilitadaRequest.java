package com.accesmed.backend.Records.Prestacion.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para actualizar los datos generales de una prestación en borrador
 * (nombre y especialidad). Solo aplicable mientras la prestación no esté habilitada.
 * Un campo en {@code null} o ausente significa "no lo toques": el {@code id} es el
 * único obligatorio.
 */
public record UpdatePrestacionNoHabilitadaRequest(

        /**
         * Identificador de la prestación a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

        /**
         * Nombre descriptivo de la prestación ({@code String}). Máximo 150 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Identificador de la especialidad ({@code UUID}) a la cual pertenece esta prestación.
         * {@code null} deja la especialidad sin tocar.
         */
        UUID especialidadId

) {
}
