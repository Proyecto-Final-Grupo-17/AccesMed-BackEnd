package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.TipoCobertura;

import java.math.BigDecimal;

/**
 * Resultado de cotizar un turno para el canal chatbot: con qué cobertura queda, cuánto paga el
 * paciente y, si pidió obra social y no cubre, por qué (Diseno-Chatbot §4.4).
 *
 * @param tipoCobertura {@code TipoCobertura} cobertura pedida por el paciente
 * @param obraSocialPaciente {@code ObraSocialPaciente} cobertura del paciente que cubre la prestación, o {@code null} si es particular o no cubre
 * @param cubre {@code boolean} {@code true} si la cobertura pedida aplica (particular siempre aplica)
 * @param codigoMotivo {@code String} por qué no cubre ({@code PACIENTE_SIN_OBRA_SOCIAL}, {@code PLAN_NO_PUBLICADO}, {@code PLAN_NO_CUBRE_PRESTACION}), o {@code null}
 * @param motivo {@code String} el motivo en español para el paciente, o {@code null} si cubre
 * @param montoAPagar {@code BigDecimal} lo que paga con la cobertura pedida, o el precio particular si no cubre
 * @param precioParticular {@code BigDecimal} precio particular del médico para la prestación
 */
public record CotizacionTurnoAgente(
        TipoCobertura tipoCobertura,
        ObraSocialPaciente obraSocialPaciente,
        boolean cubre,
        String codigoMotivo,
        String motivo,
        BigDecimal montoAPagar,
        BigDecimal precioParticular
) {
}
