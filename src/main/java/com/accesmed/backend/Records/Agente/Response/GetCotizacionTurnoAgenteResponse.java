package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.TipoCobertura;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cotización de un turno antes de reservarlo o reprogramarlo (Diseno-Chatbot §4.4): todo lo que
 * el agente muestra en el resumen previo al "¿Confirmás?". No guarda nada.
 *
 * @param fechaHoraInicio {@code LocalDateTime} fecha y hora de inicio en la zona horaria de la clínica
 * @param fechaHoraTexto {@code String} la misma fecha y hora legible, con el día de la semana; el agente la copia tal cual
 * @param nombrePrestacion {@code String} nombre de la prestación
 * @param nombreMedico {@code String} nombre del médico
 * @param apellidoMedico {@code String} apellido del médico
 * @param matricula {@code String} matrícula del médico
 * @param tipoCobertura {@code TipoCobertura} cobertura cotizada
 * @param obraSocial {@code String} obra social que cubre, o {@code null} si es particular o no cubre
 * @param plan {@code String} plan que cubre, o {@code null} si es particular o no cubre
 * @param cubre {@code boolean} {@code true} si la cobertura cotizada aplica; con {@code false} no se puede reservar con ella
 * @param motivo {@code String} por qué no cubre, en español para el paciente, o {@code null}
 * @param montoAPagar {@code BigDecimal} lo que paga con la cobertura cotizada (o el precio particular si no cubre)
 * @param precioParticular {@code BigDecimal} precio particular, para ofrecerlo si la obra social no cubre
 * @param requiereValidacion {@code boolean} {@code true} si alguna indicación requiere validación: el turno queda en espera
 * @param indicaciones {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones vigentes de la prestación
 */
public record GetCotizacionTurnoAgenteResponse(
        LocalDateTime fechaHoraInicio,
        String fechaHoraTexto,
        String nombrePrestacion,
        String nombreMedico,
        String apellidoMedico,
        String matricula,
        TipoCobertura tipoCobertura,
        String obraSocial,
        String plan,
        boolean cubre,
        String motivo,
        BigDecimal montoAPagar,
        BigDecimal precioParticular,
        boolean requiereValidacion,
        List<ListIndicacionPrestacionAgenteResponse> indicaciones
) {
}
