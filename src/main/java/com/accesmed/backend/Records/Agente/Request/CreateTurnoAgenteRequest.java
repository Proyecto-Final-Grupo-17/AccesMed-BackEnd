package com.accesmed.backend.Records.Agente.Request;

import com.accesmed.backend.Domain.TipoCobertura;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Record para que el agente solicite un turno (ECU-TURN-3). El médico y la prestación se derivan
 * del slot; la cobertura la elige el paciente (Diseno-Chatbot §4.4).
 */
public record CreateTurnoAgenteRequest(

        /**
         * Identificador opaco del horario elegido ({@code UUID}), tal como lo devolvió la
         * consulta de disponibilidad. Nunca se muestra al paciente.
         */
        @NotNull(message = "El horario elegido es obligatorio.")
        UUID slotId,

        /**
         * Cobertura que eligió el paciente: {@code PARTICULAR} u {@code OBRA_SOCIAL}. Con obra
         * social, si su plan no cubre la prestación la reserva falla (no pasa a particular sola).
         */
        @NotNull(message = "La cobertura es obligatoria: particular u obra social.")
        TipoCobertura cobertura

) {
}
