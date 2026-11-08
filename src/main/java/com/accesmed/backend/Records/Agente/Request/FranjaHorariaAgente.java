package com.accesmed.backend.Records.Agente.Request;

import java.time.LocalTime;

/**
 * Franja horaria del día para filtrar la disponibilidad: mañana (hasta las 12), tarde (de 12 a
 * 19) y noche (desde las 19).
 */
public enum FranjaHorariaAgente {

    MANANA(LocalTime.MIDNIGHT, LocalTime.NOON),
    TARDE(LocalTime.NOON, LocalTime.of(19, 0)),
    NOCHE(LocalTime.of(19, 0), null);

    private final LocalTime desde;
    private final LocalTime hasta;

    FranjaHorariaAgente(LocalTime desde, LocalTime hasta) {
        this.desde = desde;
        this.hasta = hasta;
    }

    /**
     * Devuelve la hora de inicio de la franja (inclusive).
     *
     * @return {@code LocalTime} hora de inicio
     */
    public LocalTime desde() {
        return desde;
    }

    /**
     * Devuelve la hora de fin de la franja (exclusiva).
     *
     * @return {@code LocalTime} hora de fin, o {@code null} si la franja llega hasta el fin del día
     */
    public LocalTime hasta() {
        return hasta;
    }

    /**
     * Resuelve la franja a la que pertenece una hora.
     *
     * @param hora {@code LocalTime} hora del día
     * @return {@code FranjaHorariaAgente} la franja que contiene esa hora
     */
    public static FranjaHorariaAgente of(LocalTime hora) {

        if (hora.isBefore(MANANA.hasta)) {
            return MANANA;
        }
        if (hora.isBefore(TARDE.hasta)) {
            return TARDE;
        }
        return NOCHE;

    }

}
