package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.ModalidadCobertura;

import java.math.BigDecimal;

/**
 * Estrategia de cálculo del monto a pagar de un turno, diferenciada por
 * {@link ModalidadCobertura}. La fábrica ({@link FabricaEstrategiaCalcularMontoAPagarTurno})
 * elige la implementación correcta en tiempo de ejecución según la modalidad que
 * corresponda al turno.
 */
public interface EstrategiaCalcularMontoAPagarTurno {

    /**
     * Retorna la modalidad de cobertura que esta estrategia implementa.
     *
     * @return {@code ModalidadCobertura} la modalidad que soporta esta implementación
     */
    ModalidadCobertura getModalidadSoportada();

    /**
     * Calcula el monto a pagar del turno según el contexto proporcionado.
     *
     * @param contexto {@code ContextoCalculoMontoTurno} datos del turno y cobertura
     * @return {@code BigDecimal} el monto a pagar
     */
    BigDecimal calcularMonto(ContextoCalculoMontoTurno contexto);

}
