package com.accesmed.backend.Records.Turno.Request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para crear un turno nuevo.
 *
 * @param pacienteId {@code UUID} identificador del paciente
 * @param medicoId {@code UUID} identificador del médico
 * @param prestacionId {@code UUID} identificador de la prestación
 * @param slotId {@code UUID} identificador del slot de agenda disponible
 * @param obraSocialPacienteId {@code UUID} identificador de la cobertura del paciente (opcional)
 */
public record CreateTurnoRequest(
        @NotNull(message = "El id del paciente es obligatorio.")
        UUID pacienteId,

        @NotNull(message = "El id del médico es obligatorio.")
        UUID medicoId,

        @NotNull(message = "El id de la prestación es obligatorio.")
        UUID prestacionId,

        @NotNull(message = "El id del slot de agenda es obligatorio.")
        UUID slotId,

        UUID obraSocialPacienteId
) {}
