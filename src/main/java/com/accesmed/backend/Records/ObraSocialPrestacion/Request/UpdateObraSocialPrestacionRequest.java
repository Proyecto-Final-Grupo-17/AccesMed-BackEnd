package com.accesmed.backend.Records.ObraSocialPrestacion.Request;

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
 * Record para actualizar la modalidad y los montos de cobertura de una asignación
 * plan-prestación existente. No lleva {@code planId}/{@code prestacionId}: esos no
 * cambian, solo la modalidad y sus montos.
 */
@CoherenciaCobertura
public record UpdateObraSocialPrestacionRequest(

        /**
         * Identificador de la cobertura a actualizar ({@code UUID}).
         * Debe coincidir con el id de la ruta.
         */
        @NotNull(message = "El identificador es obligatorio.")
        UUID id,

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
