package com.accesmed.backend.Records.Agente.Request;

import com.accesmed.backend.Domain.EstadoTurno;

import java.util.Set;

/**
 * Acción que el paciente quiere hacer sobre uno de sus turnos. Traduce la acción a los estados
 * vigentes en los que el turno admite esa operación (reglas de los CU ECU-TURN-4/5/6).
 */
public enum AccionTurnoAgente {

    CANCELABLE(Set.of(EstadoTurno.PENDIENTE, EstadoTurno.CONFIRMADO, EstadoTurno.ESPERA_VALIDACION)),
    REPROGRAMABLE(Set.of(EstadoTurno.PENDIENTE, EstadoTurno.ESPERA_VALIDACION)),
    CONFIRMABLE(Set.of(EstadoTurno.PENDIENTE));

    private final Set<EstadoTurno> estados;

    AccionTurnoAgente(Set<EstadoTurno> estados) {
        this.estados = estados;
    }

    /**
     * Devuelve los estados vigentes en los que el turno admite la acción.
     *
     * @return {@code Set<EstadoTurno>} estados vigentes habilitados para la acción
     */
    public Set<EstadoTurno> estados() {
        return estados;
    }

}
