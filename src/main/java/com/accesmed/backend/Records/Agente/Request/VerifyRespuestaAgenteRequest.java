package com.accesmed.backend.Records.Agente.Request;

import com.accesmed.backend.Domain.TipoCobertura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Afirmaciones que el agente hace en su respuesta, agrupadas por entidad, para verificarlas contra
 * la base antes de mandarla al paciente (Diseno-Chatbot §4.3). Todas las listas son opcionales:
 * solo se verifica lo que viene.
 *
 * @param especialidades {@code List<EspecialidadAfirmada>} especialidades nombradas
 * @param prestaciones {@code List<PrestacionAfirmada>} prestaciones nombradas
 * @param medicos {@code List<MedicoAfirmado>} médicos nombrados
 * @param horarios {@code List<HorarioAfirmado>} horarios concretos ofrecidos
 * @param disponibilidad {@code List<DisponibilidadAfirmada>} "hay" / "no hay" turnos un día
 * @param turnos {@code List<TurnoAfirmado>} turnos del paciente nombrados
 * @param cotizaciones {@code List<CotizacionAfirmada>} montos y coberturas informados
 */
public record VerifyRespuestaAgenteRequest(
        List<EspecialidadAfirmada> especialidades,
        List<PrestacionAfirmada> prestaciones,
        List<MedicoAfirmado> medicos,
        List<HorarioAfirmado> horarios,
        List<DisponibilidadAfirmada> disponibilidad,
        List<TurnoAfirmado> turnos,
        List<CotizacionAfirmada> cotizaciones
) {

    /**
     * Especialidad nombrada por el agente.
     *
     * @param nombre {@code String} nombre de la especialidad
     */
    public record EspecialidadAfirmada(String nombre) {
    }

    /**
     * Prestación nombrada por el agente, con lo que dijo de ella.
     *
     * @param nombre {@code String} nombre de la prestación
     * @param especialidad {@code String} especialidad a la que dijo que pertenece, o {@code null}
     * @param indicaciones {@code List<String>} indicaciones que nombró (texto), o {@code null}
     * @param requiereValidacion {@code Boolean} si dijo que requiere validación, o {@code null}
     */
    public record PrestacionAfirmada(String nombre, String especialidad, List<String> indicaciones,
            Boolean requiereValidacion) {
    }

    /**
     * Médico nombrado por el agente, con lo que dijo de él.
     *
     * @param matricula {@code String} matrícula del médico
     * @param nombre {@code String} nombre y/o apellido con el que lo nombró, o {@code null}
     * @param especialidad {@code String} especialidad que le atribuyó, o {@code null}
     * @param prestacion {@code String} prestación que dijo que atiende, o {@code null}
     */
    public record MedicoAfirmado(String matricula, String nombre, String especialidad, String prestacion) {
    }

    /**
     * Horario concreto ofrecido por el agente.
     *
     * @param fecha {@code LocalDate} fecha del horario
     * @param diaSemana {@code String} día de la semana que dijo, o {@code null}
     * @param hora {@code LocalTime} hora de inicio
     * @param matricula {@code String} matrícula del médico
     * @param prestacion {@code String} nombre de la prestación
     */
    public record HorarioAfirmado(LocalDate fecha, String diaSemana, LocalTime hora, String matricula,
            String prestacion) {
    }

    /**
     * Afirmación de disponibilidad de un día ("hay turnos para el lunes").
     *
     * @param fecha {@code LocalDate} día consultado
     * @param prestacion {@code String} nombre de la prestación
     * @param matricula {@code String} matrícula del médico, o {@code null} si habló de la prestación en general
     * @param hay {@code Boolean} {@code true} si dijo que hay turnos, {@code false} si dijo que no
     */
    public record DisponibilidadAfirmada(LocalDate fecha, String prestacion, String matricula, Boolean hay) {
    }

    /**
     * Turno del paciente nombrado por el agente.
     *
     * @param codigo {@code String} código del turno
     * @param fecha {@code LocalDate} fecha que dijo, o {@code null}
     * @param hora {@code LocalTime} hora que dijo, o {@code null}
     * @param estado {@code String} estado que dijo, o {@code null}
     */
    public record TurnoAfirmado(String codigo, LocalDate fecha, LocalTime hora, String estado) {
    }

    /**
     * Cotización informada por el agente para un horario.
     *
     * @param fecha {@code LocalDate} fecha del horario
     * @param hora {@code LocalTime} hora de inicio del horario
     * @param matricula {@code String} matrícula del médico
     * @param prestacion {@code String} nombre de la prestación
     * @param cobertura {@code TipoCobertura} cobertura informada
     * @param plan {@code String} plan informado (obra social), o {@code null}
     * @param monto {@code BigDecimal} monto informado
     */
    public record CotizacionAfirmada(LocalDate fecha, LocalTime hora, String matricula, String prestacion,
            TipoCobertura cobertura, String plan, BigDecimal monto) {
    }

}
