package com.accesmed.backend.Domain;

/**
 * Estados del ciclo de vida de un {@link Plan}: mismo esquema que {@link EstadoPrestacion}
 * — {@code No Publicado ⇄ Publicado} es reversible; {@code Deshabilitado} es terminal.
 */
public enum EstadoPlan {

    NO_PUBLICADO,
    PUBLICADO,
    DESHABILITADO

}
