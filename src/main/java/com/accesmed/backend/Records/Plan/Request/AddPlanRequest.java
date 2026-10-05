package com.accesmed.backend.Records.Plan.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Record para agregar un plan nuevo a una obra social existente. Nace en estado
 * {@code NO_PUBLICADO}.
 */
public record AddPlanRequest(

        /**
         * Identificador de la obra social ({@code UUID}) a la cual pertenece este plan.
         */
        @NotNull(message = "El identificador de obra social es obligatorio.")
        UUID obraSocialId,

        /**
         * Código del plan ({@code String}, único dentro de la obra social). Máximo 20 caracteres.
         */
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String codigo,

        /**
         * Nombre del plan ({@code String}). Máximo 150 caracteres.
         */
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Coberturas de prestaciones iniciales del plan ({@code List<AsignarCoberturaAnidadaRequest>}),
         * opcionales. Si se proporciona, cada elemento se valida con {@link Valid}.
         */
        @Valid
        List<AsignarCoberturaAnidadaRequest> coberturas

) {
}
