package com.accesmed.backend.Records.Turno.Request;

import com.accesmed.backend.Domain.MotivoCancelacion;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para cancelar un turno.
 *
 * @param id identificador del turno a cancelar
 * @param motivo motivo de la cancelación (opcional; por defecto SOLICITUD_DEL_PACIENTE)
 */
public record CancelTurnoRequest(
        @NotNull(message = "El id del turno es obligatorio.")
        UUID id,

        MotivoCancelacion motivo
) {}
