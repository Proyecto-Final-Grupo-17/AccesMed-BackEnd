package com.accesmed.backend.Records.Turno.Request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para reprogramar un turno existente con un nuevo slot.
 *
 * @param id identificador del turno a reprogramar
 * @param slotId identificador del nuevo slot de agenda
 */
public record ReprogramTurnoRequest(
        @NotNull(message = "El id del turno es obligatorio.")
        UUID id,

        @NotNull(message = "El id del nuevo slot de agenda es obligatorio.")
        UUID slotId
) {}
