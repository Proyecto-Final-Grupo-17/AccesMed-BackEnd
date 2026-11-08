package com.accesmed.backend.Records.Agente.Request;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Turno;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Function;

/**
 * Acción que el paciente quiere hacer sobre uno de sus turnos. Traduce la acción a los estados
 * vigentes en los que el turno admite esa operación y a la fecha límite que aplica (reglas de los
 * CU ECU-TURN-4/5/6).
 */
public enum AccionTurnoAgente {

    CANCELABLE(Set.of(EstadoTurno.PENDIENTE, EstadoTurno.CONFIRMADO, EstadoTurno.ESPERA_VALIDACION),
            Turno::getFechaLimiteCancelacion),
    REPROGRAMABLE(Set.of(EstadoTurno.PENDIENTE), Turno::getFechaLimiteReprogramacion),
    CONFIRMABLE(Set.of(EstadoTurno.PENDIENTE), Turno::getFechaLimiteConfirmacion);

    private final Set<EstadoTurno> estados;
    private final Function<Turno, ZonedDateTime> fechaLimite;

    AccionTurnoAgente(Set<EstadoTurno> estados, Function<Turno, ZonedDateTime> fechaLimite) {
        this.estados = estados;
        this.fechaLimite = fechaLimite;
    }

    /**
     * Devuelve los estados vigentes en los que el turno admite la acción.
     *
     * @return {@code Set<EstadoTurno>} estados vigentes habilitados para la acción
     */
    public Set<EstadoTurno> estados() {
        return estados;
    }

    /**
     * Devuelve la fecha límite del turno para esta acción: pasada esa fecha la operación
     * correspondiente responde fuera de plazo.
     *
     * @param turno {@code Turno} turno del que se toma la fecha límite
     * @return {@code ZonedDateTime} fecha límite de cancelación, reprogramación o confirmación según la acción
     */
    public ZonedDateTime fechaLimite(Turno turno) {
        return fechaLimite.apply(turno);
    }

}
