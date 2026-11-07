package com.accesmed.backend.Records.Agente.Request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Record para que el agente solicite un turno (ECU-TURN-3). El médico, la prestación y la
 * cobertura se derivan del slot y del paciente identificado por el header.
 */
public record CreateTurnoAgenteRequest(

        /**
         * Identificador opaco del horario elegido ({@code UUID}), tal como lo devolvió la
         * consulta de disponibilidad. Nunca se muestra al paciente.
         */
        @NotNull(message = "El horario elegido es obligatorio.")
        UUID slotId

) {
}
