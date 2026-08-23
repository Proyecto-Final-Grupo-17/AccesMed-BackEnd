package com.accesmed.backend.Schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Recorre los turnos en estado {@code Confirmado} con {@code fechaLimiteAnuncioTardio}
 * vencida y los transiciona a {@code Ausente}, reutilizando el mismo método de
 * {@code Application} que usaría un CU manual equivalente.
 *
 * <p>Corre después de {@link ConfirmarTurnosAutomaticamenteScheduler} en cada barrido. No
 * tiene notificación mapeada (pendiente de decisión, ver {@code Notifications/TipoNotificacionTurno}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarcarAusentesScheduler {

}
