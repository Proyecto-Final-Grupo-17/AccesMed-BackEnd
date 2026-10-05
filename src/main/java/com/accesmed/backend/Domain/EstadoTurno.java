package com.accesmed.backend.Domain;

import java.util.Set;

/**
 * Estados del ciclo de vida de un {@link Turno} (DTE {@code accesmed/dte/3.0}).
 * Los finales no se marcan a nivel de esquema: {@code esFinal} es una constante de
 * código que consumen las guardas de "estado no final" (ej. bajas restrictivas).
 */
public enum EstadoTurno {

    ESPERA_VALIDACION,
    PENDIENTE,
    CONFIRMADO,
    EN_SALA_DE_ESPERA,
    EN_CURSO,
    CANCELADO,
    REPROGRAMADO,
    AUSENTE,
    FINALIZADO;

    /**
     * Estados finales: no admiten más transiciones. Es la constante de código que
     * consumen las guardas de "estado no final" (ej. bajas restrictivas de Prestación y
     * Plan contra turnos vivos).
     */
    public static final Set<EstadoTurno> FINALES = Set.of(CANCELADO, REPROGRAMADO, AUSENTE, FINALIZADO);

    /**
     * Indica si este estado es final (no admite más transiciones).
     *
     * @return {@code boolean} {@code true} si el estado es final
     */
    public boolean esFinal() {
        return FINALES.contains(this);
    }

}
