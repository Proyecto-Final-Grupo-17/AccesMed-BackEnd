package com.accesmed.backend.Schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Recorre los turnos en estado {@code Pendiente} con {@code fechaHoraRecordatorioConfirmacion}
 * vencida y dispara el recordatorio de confirmación (NOTIF-6) por cada uno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordarConfirmacionScheduler {

}
