package com.accesmed.backend.Schedulers;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Scheduler de turno: orquesta los barridos periódicos del estado de todos los turnos
 * para ejecutar transiciones automáticas según fechas límite. Reemplaza a los cuatro
 * stubs anteriores (`RecordarConfirmacionScheduler`, `ConfirmarTurnosAutomaticamenteScheduler`,
 * `VencerValidacionTurnoScheduler`, `MarcarAusentesScheduler`) en una única clase.
 *
 * El flujo es:
 * 1. El método `@Scheduled` se ejecuta en su intervalo de cron.
 * 2. Consulta el estado vigente de todos los turnos en el estado correspondiente.
 * 3. Filtra en memoria por su fecha límite específica.
 * 4. Para cada turno vencido, llama al bean transaccional {@code TurnoSchedulerService}.
 * 5. Si uno falla, la excepción se atrapa y loguea; los demás continúan.
 *
 * Inyecta solo {@code TurnoDomainService} (para consultar) y {@code TurnoSchedulerService}
 * (para ejecutar transaccionalmente) — nunca accede a Repository directamente ni depende de
 * la capa Application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurnoScheduler {

    //region ========== Dependencias o inyecciones ==========

    private final TurnoDomainService turnoDomainService;
    private final TurnoSchedulerService turnoSchedulerService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Confirma automáticamente todos los turnos en estado {@code PENDIENTE} cuya fecha
     * límite de confirmación ya venció. Cron configurable vía `accesmed.scheduler.confirmar-turnos-automaticamente.cron`.
     * Guarda de concurrencia: reconfirma el estado vigente en el método de transición,
     * así que un turno confirmado manualmente en paralelo fallará con {@code ReglaNegocioException}
     * y se seguirá procesando el resto del lote.
     */
    @Scheduled(cron = "${accesmed.scheduler.confirmar-turnos-automaticamente.cron}")
    public void confirmarTurnosVencidos() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaLimiteConfirmacion())) {
                try {
                    turnoSchedulerService.confirmarTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo confirmando automáticamente el turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    /**
     * Vence automáticamente la validación de todos los turnos en estado
     * {@code ESPERA_VALIDACION} cuya fecha límite de validación ya venció. Cron configurable
     * vía `accesmed.scheduler.vencer-validacion-turno.cron`. Libera el slot del turno,
     * marca su motivo de cancelación como {@code VALIDACION_VENCIDA}, transiciona a
     * {@code CANCELADO} y notifica al paciente. Guarda de concurrencia: ídem a
     * confirmación automática.
     */
    @Scheduled(cron = "${accesmed.scheduler.vencer-validacion-turno.cron}")
    public void vencerValidacionesTurno() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.ESPERA_VALIDACION);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaLimiteValidacion())) {
                try {
                    turnoSchedulerService.vencerValidacionTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo venciendo la validación del turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    /**
     * Marca automáticamente como {@code AUSENTE} todos los turnos en estado
     * {@code CONFIRMADO} cuya fecha límite de anuncio tardío ya venció. Cron configurable
     * vía `accesmed.scheduler.marcar-ausentes.cron`. Transiciona a {@code AUSENTE} y
     * notifica al paciente. Guarda de orden: este scheduler corre después de
     * {@code confirmarTurnosVencidos}, así que un turno recién confirmado no puede
     * aparecer en este lote hasta el barrido siguiente.
     */
    @Scheduled(cron = "${accesmed.scheduler.marcar-ausentes.cron}")
    public void marcarAusentesTurno() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.CONFIRMADO);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaLimiteAnuncioTardio())) {
                try {
                    turnoSchedulerService.marcarAusenteTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo marcando ausente el turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    /**
     * Recuerda automáticamente al paciente que debe confirmar su turno. Busca todos los
     * turnos en estado {@code PENDIENTE} cuya fecha de recordatorio ya llegó y envía una
     * notificación. Cron configurable vía `accesmed.scheduler.recordar-confirmacion.cron`.
     * No transiciona estado: solo notifica al paciente. Guarda de concurrencia: un turno
     * que fue confirmado manualmente fallará con {@code ReglaNegocioException}; se sigue
     * procesando el resto.
     */
    @Scheduled(cron = "${accesmed.scheduler.recordar-confirmacion.cron}")
    public void recordarConfirmacionTurno() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaHoraRecordatorioConfirmacion())) {
                try {
                    turnoSchedulerService.recordarConfirmacionTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo recordando confirmación del turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    //endregion

}
