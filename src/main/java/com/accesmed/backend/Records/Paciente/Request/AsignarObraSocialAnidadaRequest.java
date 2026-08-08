package com.accesmed.backend.Records.Paciente.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Record de cobertura de obra social anidada dentro de la creación de un paciente nuevo.
 */
public record AsignarObraSocialAnidadaRequest(

        /**
         * Identificador de la obra social existente ({@code UUID}).
         */
        @NotNull(message = "La obra social es obligatoria.")
        UUID obraSocialId,

        /**
         * Identificador del plan existente ({@code UUID}). Debe pertenecer a la obra social indicada.
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
