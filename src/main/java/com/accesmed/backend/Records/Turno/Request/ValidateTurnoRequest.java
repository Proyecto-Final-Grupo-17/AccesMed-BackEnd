package com.accesmed.backend.Records.Turno.Request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para validar (aprobar o rechazar) un turno en estado ESPERA_VALIDACION.
 *
 * @param id identificador del turno a validar
 * @param aprobado {@code true} para aprobar, {@code false} para rechazar
 */
public record ValidateTurnoRequest(
        @NotNull(message = "El id del turno es obligatorio.")
        UUID id,

        @NotNull(message = "El campo aprobado es obligatorio.")
        Boolean aprobado
) {}
