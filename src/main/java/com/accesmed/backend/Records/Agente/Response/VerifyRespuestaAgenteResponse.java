package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.CotizacionAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.DisponibilidadAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.EspecialidadAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.HorarioAfirmado;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.MedicoAfirmado;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.PrestacionAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.TurnoAfirmado;

import java.util.List;

/**
 * Resultado de verificar las afirmaciones de una respuesta del agente contra la base, con la misma
 * estructura que el request: cada ítem trae su veredicto (Diseno-Chatbot §4.3).
 *
 * @param verdadero {@code boolean} {@code true} si todas las afirmaciones son verdaderas
 * @param especialidades {@code List<ItemVerificado<EspecialidadAfirmada>>} veredicto por especialidad
 * @param prestaciones {@code List<ItemVerificado<PrestacionAfirmada>>} veredicto por prestación
 * @param medicos {@code List<ItemVerificado<MedicoAfirmado>>} veredicto por médico
 * @param horarios {@code List<ItemVerificado<HorarioAfirmado>>} veredicto por horario
 * @param disponibilidad {@code List<ItemVerificado<DisponibilidadAfirmada>>} veredicto por afirmación de disponibilidad
 * @param turnos {@code List<ItemVerificado<TurnoAfirmado>>} veredicto por turno
 * @param cotizaciones {@code List<ItemVerificado<CotizacionAfirmada>>} veredicto por cotización
 */
public record VerifyRespuestaAgenteResponse(
        boolean verdadero,
        List<ItemVerificado<EspecialidadAfirmada>> especialidades,
        List<ItemVerificado<PrestacionAfirmada>> prestaciones,
        List<ItemVerificado<MedicoAfirmado>> medicos,
        List<ItemVerificado<HorarioAfirmado>> horarios,
        List<ItemVerificado<DisponibilidadAfirmada>> disponibilidad,
        List<ItemVerificado<TurnoAfirmado>> turnos,
        List<ItemVerificado<CotizacionAfirmada>> cotizaciones
) {

    /**
     * Veredicto de una afirmación.
     *
     * @param dato {@code T} la afirmación tal como llegó
     * @param verdadero {@code boolean} {@code true} si coincide con la base
     * @param motivo {@code String} por qué es falsa, en español para el modelo, o {@code null} si es verdadera
     * @param correcto {@code String} el valor real cuando se puede dar (por ejemplo {@code viernes}), o {@code null}
     * @param <T> tipo de la afirmación
     */
    public record ItemVerificado<T>(T dato, boolean verdadero, String motivo, String correcto) {

        /**
         * Veredicto de una afirmación verdadera.
         *
         * @param dato {@code T} la afirmación
         * @param <T> tipo de la afirmación
         * @return {@code ItemVerificado<T>} ítem verdadero, sin motivo ni valor correcto
         */
        public static <T> ItemVerificado<T> aprobado(T dato) {

            ItemVerificado<T> itemVerdadero = new ItemVerificado<>(dato, true, null, null);
            return itemVerdadero;

        }

        /**
         * Veredicto de una afirmación falsa.
         *
         * @param dato {@code T} la afirmación
         * @param motivo {@code String} por qué es falsa
         * @param correcto {@code String} valor real, o {@code null} si no se puede dar
         * @param <T> tipo de la afirmación
         * @return {@code ItemVerificado<T>} ítem falso con su motivo
         */
        public static <T> ItemVerificado<T> rechazado(T dato, String motivo, String correcto) {

            ItemVerificado<T> itemFalso = new ItemVerificado<>(dato, false, motivo, correcto);
            return itemFalso;

        }

    }

}
