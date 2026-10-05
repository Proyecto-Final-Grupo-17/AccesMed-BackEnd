package com.accesmed.backend.Notifications;

import com.accesmed.backend.Notifications.Canales.CanalNotificacionTurno;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacciona a {@link TurnoNotificacionEvent} y le delega la notificación a cada
 * {@link CanalNotificacionTurno} activo (Spring inyecta todos los {@code @Component} que
 * implementan la interfaz). Agregar un canal nuevo (mail, push, etc.) no requiere tocar
 * este listener ni el {@code App} que publica el evento.
 *
 * <p>Un canal que falla no interrumpe a los demás: se loguea y se sigue con el resto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurnoNotificacionListener {

    //region ========== Dependencias o inyecciones ==========

    private final List<CanalNotificacionTurno> canalesNotificacionTurno;

    //endregion

    //region ========== Métodos ==========

    /**
     * Escucha {@code turnoNotificacionEvent} y le delega la notificación a cada canal
     * activo, aislando la falla de uno del resto.
     *
     * @param turnoNotificacionEvent {@code TurnoNotificacionEvent} con el tipo de evento
     *                                y el turno notificado
     */
    @EventListener
    public void escucharTurnoNotificacionEvent(TurnoNotificacionEvent turnoNotificacionEvent) {
        for (CanalNotificacionTurno canalNotificacionTurno : canalesNotificacionTurno) {
            try {
                canalNotificacionTurno.notificar(turnoNotificacionEvent.tipo(), turnoNotificacionEvent.turno());
            } catch (RuntimeException excepcion) {
                log.error(
                    "Fallo el canal {} notificando el turno {} (tipo {})",
                    canalNotificacionTurno.getClass().getSimpleName(),
                    turnoNotificacionEvent.turno().getId(),
                    turnoNotificacionEvent.tipo(),
                    excepcion
                );
            }
        }
    }

    //endregion
}
