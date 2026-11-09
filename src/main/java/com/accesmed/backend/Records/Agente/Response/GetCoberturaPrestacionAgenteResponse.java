package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Domain.ModalidadCobertura;

import java.math.BigDecimal;
import java.util.List;

/**
 * Qué cubre una obra social y plan para una prestación y cuánto pagaría el paciente, sin elegir un
 * horario ({@code GET /Agente/Prestacion/Cobertura}). Trae una cobertura por cada plan consultado.
 *
 * @param prestacion {@code String} nombre de la prestación consultada
 * @param coberturas {@code List<CoberturaPlan>} resultado por plan (uno si se nombró obra social y plan)
 */
public record GetCoberturaPrestacionAgenteResponse(
        String prestacion,
        List<CoberturaPlan> coberturas
) {

    /**
     * Cobertura de un plan para la prestación.
     *
     * @param obraSocial {@code String} obra social (o la que dijo el paciente si no existe)
     * @param plan {@code String} plan consultado, o {@code null} si la obra social o el plan no existen
     * @param cubre {@code boolean} {@code true} si el plan cubre la prestación hoy
     * @param modalidad {@code ModalidadCobertura} cómo se calcula el monto, o {@code null} si no cubre
     * @param porcentajeCobertura {@code BigDecimal} porcentaje que cubre (modalidad {@code PORCENTUAL}), o {@code null}
     * @param coseguro {@code BigDecimal} cargo fijo (modalidad {@code CARGO_FIJO}), o {@code null}
     * @param codigoMotivo {@code String} por qué no cubre ({@code OBRA_SOCIAL_NO_ENCONTRADA}, {@code PLAN_NO_ENCONTRADO}, {@code PLAN_NO_PUBLICADO}, {@code PLAN_NO_CUBRE_PRESTACION}), o {@code null}
     * @param motivo {@code String} el motivo en español para el paciente, o {@code null} si cubre
     * @param montos {@code List<MontoMedico>} cuánto paga con cada médico que hace la prestación (el precio particular es de cada médico)
     */
    public record CoberturaPlan(String obraSocial, String plan, boolean cubre, ModalidadCobertura modalidad,
            BigDecimal porcentajeCobertura, BigDecimal coseguro, String codigoMotivo, String motivo,
            List<MontoMedico> montos) {
    }

    /**
     * Monto de la prestación con un médico.
     *
     * @param nombre {@code String} nombre del médico
     * @param apellido {@code String} apellido del médico
     * @param matricula {@code String} matrícula del médico
     * @param precioParticular {@code BigDecimal} precio particular del médico
     * @param montoAPagar {@code BigDecimal} lo que paga con este plan (el precio particular si no cubre)
     */
    public record MontoMedico(String nombre, String apellido, String matricula, BigDecimal precioParticular,
            BigDecimal montoAPagar) {
    }

}
