package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.ModalidadCobertura;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Estrategia de cálculo para modalidad de cobertura CARGO_FIJO.
 * El monto a pagar es el coseguro fijo especificado en la cobertura.
 */
@Component
public class EstrategiaCalcularMontoAPagarTurnoCargoFijo implements EstrategiaCalcularMontoAPagarTurno {

    @Override
    public ModalidadCobertura getModalidadSoportada() {
        return ModalidadCobertura.CARGO_FIJO;
    }

    @Override
    public BigDecimal calcularMonto(ContextoCalculoMontoTurno contexto) {
        return contexto.coseguro();
    }

}
