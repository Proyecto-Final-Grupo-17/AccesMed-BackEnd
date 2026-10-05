package com.accesmed.backend.Services.Utils;

import java.math.BigDecimal;

/**
 * Contexto de cálculo del monto a pagar de un turno. Agrupa los datos necesarios
 * para que una {@link EstrategiaCalcularMontoAPagarTurno} calcule el monto según
 * la modalidad de cobertura.
 *
 * @param precioParticular {@code BigDecimal} precio que cobra el médico por la prestación
 * @param porcentajeCobertura {@code BigDecimal} porcentaje de cobertura (para modalidad porcentual)
 * @param coseguro {@code BigDecimal} monto fijo de coseguro (para modalidad cargo fijo)
 */
public record ContextoCalculoMontoTurno(BigDecimal precioParticular, BigDecimal porcentajeCobertura, BigDecimal coseguro) {
}
