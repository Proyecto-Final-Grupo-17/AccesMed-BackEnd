package com.accesmed.backend.Controllers.Validators;

import com.accesmed.backend.Domain.ModalidadCobertura;

import java.math.BigDecimal;

/**
 * Contrato mínimo de un record que declara una cobertura (modalidad + porcentaje +
 * coseguro), para que {@link CoherenciaCoberturaValidator} valide la coherencia entre
 * los tres campos sin acoplarse a un record concreto. Lo implementan tanto
 * {@code AssignObraSocialPrestacionRequest} como {@code AsignarCoberturaAnidadaRequest}
 * y {@code UpdateObraSocialPrestacionRequest}.
 */
public interface TieneCobertura {

    /**
     * Modalidad de cobertura declarada.
     *
     * @return {@code ModalidadCobertura} modalidad de cobertura
     */
    ModalidadCobertura modalidadCobertura();

    /**
     * Porcentaje de cobertura declarado (aplica solo a {@code PORCENTUAL}).
     *
     * @return {@code BigDecimal} porcentaje de cobertura, o {@code null}
     */
    BigDecimal porcentajeCobertura();

    /**
     * Coseguro fijo declarado (aplica solo a {@code CARGO_FIJO}).
     *
     * @return {@code BigDecimal} coseguro, o {@code null}
     */
    BigDecimal coseguro();

}
