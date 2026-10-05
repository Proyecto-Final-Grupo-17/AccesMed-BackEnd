package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.ModalidadCobertura;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Estrategia de cálculo para modalidad de cobertura TOTAL.
 * Cuando la obra social cubre el 100% de la prestación, el monto a pagar es cero.
 */
@Component
public class EstrategiaCalcularMontoAPagarTurnoTotal implements EstrategiaCalcularMontoAPagarTurno {

    @Override
    public ModalidadCobertura getModalidadSoportada() {
        return ModalidadCobertura.TOTAL;
    }

    @Override
    public BigDecimal calcularMonto(ContextoCalculoMontoTurno contexto) {
        return BigDecimal.ZERO;
    }

}
