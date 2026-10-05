package com.accesmed.backend.Records.Plan.Request;

import com.accesmed.backend.Controllers.Validators.CoherenciaCobertura;
import com.accesmed.backend.Controllers.Validators.TieneCobertura;
import com.accesmed.backend.Domain.ModalidadCobertura;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record de cobertura de prestación anidada dentro de la creación de un plan (nuevo o
 * anidado en el alta de una obra social).
 */
@CoherenciaCobertura
public record AsignarCoberturaAnidadaRequest(

        /**
         * Identificador de la prestación existente a cubrir ({@code UUID}).
         */
        @NotNull(message = "La prestación es obligatoria.")
        UUID prestacionId,

        /**
         * Modalidad de cobertura ({@code ModalidadCobertura}).
         */
        @NotNull(message = "La modalidad de cobertura es obligatoria.")
        ModalidadCobertura modalidadCobertura,

        /**
         * Porcentaje de cobertura ({@code BigDecimal}, entre 0 y 100). Obligatorio solo si
         * {@code modalidadCobertura} es {@code PORCENTUAL} (ver {@code @CoherenciaCobertura}).
         */
        @DecimalMin(value = "0", message = "El porcentaje de cobertura no puede ser negativo.")
        @DecimalMax(value = "100", message = "El porcentaje de cobertura no puede superar 100.")
        BigDecimal porcentajeCobertura,

        /**
         * Coseguro fijo a cargo del paciente ({@code BigDecimal}, no negativo). Obligatorio
         * solo si {@code modalidadCobertura} es {@code CARGO_FIJO} (ver {@code @CoherenciaCobertura}).
         */
        @PositiveOrZero(message = "El coseguro no puede ser negativo.")
        BigDecimal coseguro

) implements TieneCobertura {
}
