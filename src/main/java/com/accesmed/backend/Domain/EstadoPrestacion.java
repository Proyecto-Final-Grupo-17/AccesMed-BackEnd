package com.accesmed.backend.Domain;

/**
 * Estados del ciclo de vida de una {@link Prestacion}: {@code No Publicada ⇄ Publicada}
 * es reversible; {@code Deshabilitada} es terminal (equivale a la baja lógica del eje
 * "estados") y no admite transición de vuelta.
 */
public enum EstadoPrestacion {

    NO_PUBLICADA,
    PUBLICADA,
    DESHABILITADA

}
