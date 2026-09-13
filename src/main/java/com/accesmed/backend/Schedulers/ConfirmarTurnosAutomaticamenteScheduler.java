package com.accesmed.backend.Schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Recorre los turnos en estado {@code Pendiente} con {@code fechaLimiteConfirmacion}
 * vencida y los transiciona a {@code Confirmado}, reutilizando el mismo método de
 * {@code Application} que usa el CU manual de confirmación.
 *
 * <p>Corre antes que {@link MarcarAusentesScheduler} en cada barrido: un turno no puede
 * saltar de {@code Pendiente} a {@code Ausente} sin pasar por {@code Confirmado}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmarTurnosAutomaticamenteScheduler {

}
