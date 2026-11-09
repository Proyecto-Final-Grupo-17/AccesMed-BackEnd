package com.accesmed.backend.Records.Agente.Request;

import java.time.DayOfWeek;

/**
 * Día de la semana que el paciente pide ("los jueves"), en español como lo escribe el agente.
 */
public enum DiaSemanaAgente {

    LUNES(DayOfWeek.MONDAY),
    MARTES(DayOfWeek.TUESDAY),
    MIERCOLES(DayOfWeek.WEDNESDAY),
    JUEVES(DayOfWeek.THURSDAY),
    VIERNES(DayOfWeek.FRIDAY),
    SABADO(DayOfWeek.SATURDAY),
    DOMINGO(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    DiaSemanaAgente(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Devuelve el día de la semana equivalente de {@code java.time}.
     *
     * @return {@code DayOfWeek} el mismo día de la semana
     */
    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

}
