package com.accesmed.backend.Records.ObraSocial.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Record para la creación de una obra social nueva, junto con al menos un plan
 * (alta atómica: obra social + planes en la misma transacción).
 */
public record CreateObraSocialRequest(

        /**
         * Código único de la obra social ({@code String}). Máximo 20 caracteres.
         */
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre descriptivo de la obra social ({@code String}). Máximo 150 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Razón social de la obra social ({@code String}). Máximo 200 caracteres.
         */
        @NotBlank(message = "La razón social es obligatoria.")
        @Size(max = 200, message = "La razón social no puede exceder 200 caracteres.")
        String razonSocial,

        /**
         * Planes iniciales de la obra social ({@code List<CreatePlanAnidadoRequest>}).
         * Al menos uno es obligatorio.
         */
        @NotEmpty(message = "La obra social debe tener al menos un plan.")
        @Valid
        List<CreatePlanAnidadoRequest> planes

) {
}
