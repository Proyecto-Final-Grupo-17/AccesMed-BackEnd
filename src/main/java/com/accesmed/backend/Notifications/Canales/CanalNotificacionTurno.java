package com.accesmed.backend.Notifications.Canales;

import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;

/**
 * Puerto de un canal concreto de notificación de {@code Turno} (WhatsApp, mail, etc.).
 * Cada canal se implementa como un adaptador independiente y se registra como
 * {@code @Component}; {@code TurnoNotificacionListener} inyecta todos los canales activos
 * y le delega a cada uno el mismo evento, sin saber cuántos ni cuáles hay.
 */
public interface CanalNotificacionTurno {

    /**
     * Notifica al paciente, por este canal, un evento de su {@code turno}.
     *
     * @param tipo  {@link TipoNotificacionTurno} que identifica el evento a notificar
     * @param turno {@link Turno} sobre el que ocurrió el evento
     */
    void notificar(TipoNotificacionTurno tipo, Turno turno);
}
