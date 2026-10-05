package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.ModalidadCobertura;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Estrategia de cálculo para modalidad de cobertura PORCENTUAL.
 * El monto a pagar se calcula como un porcentaje del precio particular:
 * monto = precioParticular × porcentajeCobertura / 100, redondeado a 2 decimales.
 */
@Component
public class EstrategiaCalcularMontoAPagarTurnoPorcentual implements EstrategiaCalcularMontoAPagarTurno {

    @Override
    public ModalidadCobertura getModalidadSoportada() {
        return ModalidadCobertura.PORCENTUAL;
    }

    @Override
    public BigDecimal calcularMonto(ContextoCalculoMontoTurno contexto) {
        return contexto.precioParticular()
                .multiply(contexto.porcentajeCobertura())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

}
