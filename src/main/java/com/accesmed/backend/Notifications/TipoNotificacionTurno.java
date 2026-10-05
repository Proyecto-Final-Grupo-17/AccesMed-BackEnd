package com.accesmed.backend.Notifications;

/**
 * Tipo de notificación de {@code Turno} que se le envía al paciente por WhatsApp, según el
 * evento que la dispara.
 */
public enum TipoNotificacionTurno {
    REGISTRADO,
    CONFIRMADO,
    CANCELADO,
    REPROGRAMADO,
    NO_VALIDADO,
    RECORDATORIO_CONFIRMACION,
    AUSENTE
}
