package com.accesmed.backend.Schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Recorre los turnos en estado {@code EsperaValidacion} con {@code fechaLimiteValidacion}
 * vencida y los cancela con {@code motivoCancelacion = VALIDACION_VENCIDA}, reutilizando el
 * mismo método de cancelación de {@code Application} que usa el CU manual de rechazo.
 *
 * <p>Esta cancelación dispara NOTIF-5 (Turno No Validado), no NOTIF-3, porque el
 * {@code TurnoNotificacionEvent} publicado lleva el motivo de cancelación y cada canal
 * branchea el mensaje según ese motivo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VencerValidacionTurnoScheduler {

}
