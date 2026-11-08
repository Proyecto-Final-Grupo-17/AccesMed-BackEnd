package com.accesmed.backend.Records.Agente.Request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Record para que el agente reprograme un turno del paciente en otro horario (ECU-TURN-5). El
 * turno se identifica por el {@code codigo} de la ruta.
 */
public record ReprogramTurnoAgenteRequest(

        /**
         * Identificador opaco del nuevo horario ({@code UUID}) del mismo médico y prestación,
         * tal como lo devolvió la consulta de disponibilidad.
         */
        @NotNull(message = "El nuevo horario es obligatorio.")
        UUID slotId

) {
}
