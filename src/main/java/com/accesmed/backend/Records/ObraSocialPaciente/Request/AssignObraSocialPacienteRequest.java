package com.accesmed.backend.Records.ObraSocialPaciente.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record para asignar a un paciente existente una cobertura sobre un plan existente de
 * una obra social existente.
 */
public record AssignObraSocialPacienteRequest(

        /**
         * Identificador del paciente ({@code UUID}).
         */
        @NotNull(message = "El paciente es obligatorio.")
        UUID pacienteId,

        /**
         * Identificador de la obra social ({@code UUID}).
         */
        @NotNull(message = "La obra social es obligatoria.")
        UUID obraSocialId,

        /**
         * Identificador del plan ({@code UUID}). Debe pertenecer a la obra social indicada.
         */
        @NotNull(message = "El plan es obligatorio.")
        UUID planId,

        /**
         * Número de socio del paciente en el plan ({@code String}). Máximo 50 caracteres.
         */
        @NotBlank(message = "El número de socio es obligatorio.")
        @Size(max = 50, message = "El número de socio no puede exceder 50 caracteres.")
        String nroSocio

) {
}
