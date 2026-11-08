package com.accesmed.backend.Records.Agente.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cobertura de obra social declarada por el paciente en el chat, identificada por nombres (el
 * agente no maneja ids). Se resuelve contra {@code ObraSocial} y {@code Plan} activos.
 */
public record ObraSocialAgenteRequest(

        /**
         * Nombre de la obra social ({@code String}).
         */
        @NotBlank(message = "El nombre de la obra social es obligatorio.")
        @Size(max = 150, message = "El nombre de la obra social no puede exceder 150 caracteres.")
        String nombreObraSocial,

        /**
         * Nombre del plan dentro de la obra social ({@code String}).
         */
        @NotBlank(message = "El nombre del plan es obligatorio.")
        @Size(max = 150, message = "El nombre del plan no puede exceder 150 caracteres.")
        String nombrePlan,

        /**
         * Número de socio del paciente en el plan ({@code String}). Máximo 50 caracteres.
         */
        @NotBlank(message = "El número de socio es obligatorio.")
        @Size(max = 50, message = "El número de socio no puede exceder 50 caracteres.")
        String nroSocio

) {
}
