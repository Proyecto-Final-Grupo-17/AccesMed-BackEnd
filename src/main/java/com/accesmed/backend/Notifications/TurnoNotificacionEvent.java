package com.accesmed.backend.Notifications;

import com.accesmed.backend.Domain.Turno;

/**
 * Evento de dominio que representa la necesidad de notificar al paciente sobre un
 * {@code turno}. El {@code App} lo publica al final de la orquestación de cada caso de
 * uso (vía {@code ApplicationEventPublisher}) sin conocer por qué canal ni cómo se va a
 * notificar; {@link TurnoNotificacionListener} es quien reacciona y reparte entre los
 * canales activos.
 *
 * @param tipo  {@link TipoNotificacionTurno} que identifica el evento a notificar
 * @param turno {@link Turno} sobre el que ocurrió el evento
 */
public record TurnoNotificacionEvent(TipoNotificacionTurno tipo, Turno turno) {
}
