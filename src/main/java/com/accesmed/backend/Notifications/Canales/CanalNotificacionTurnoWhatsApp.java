package com.accesmed.backend.Notifications.Canales;

import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link CanalNotificacionTurno} que arma el mensaje según el
 * {@link TipoNotificacionTurno} y lo despacha por WhatsApp (integración externa, ver
 * {@code ARQUITECTURA.md} / dominio §11.7). Primer canal en implementarse; el canal de
 * mail se suma más adelante como un adaptador más, sin tocar esta clase.
 */
@Slf4j
@Component
public class CanalNotificacionTurnoWhatsApp implements CanalNotificacionTurno {

    @Override
    public void notificar(TipoNotificacionTurno tipo, Turno turno) {
    }
}
