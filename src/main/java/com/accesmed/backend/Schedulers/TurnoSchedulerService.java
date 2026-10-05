package com.accesmed.backend.Schedulers;

import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Notifications.TurnoNotificacionEvent;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica transaccional para las transiciones automáticas de turnos, dispuestas por
 * {@code TurnoScheduler}. Cada método es {@code @Transactional} y resuelve la atomicidad
 * por turno: si algún paso falla (recarga de estado concurrente, fallo de BD), el turno
 * queda íntegro sin cambios. Se inyecta como un bean separado para que Spring intercepte
 * las transacciones — si se llama desde el propio bean scheduler vía {@code this.metodo()},
 * el proxy no se activa y la transacción no se abre de verdad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoSchedulerService {

    //region ========== Dependencias o inyecciones ==========

    private final TurnoDomainService turnoDomainService;
    private final HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    private final AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    private final ApplicationEventPublisher applicationEventPublisher;

    //endregion

    //region ========== Métodos ==========

    /**
     * Confirma automáticamente un turno que venció su plazo de confirmación manual.
     * Transiciona de {@code PENDIENTE} a {@code CONFIRMADO} y publica el evento de
     * notificación correspondiente. El turno se pasa ya cargado desde el scheduler para
     * minimizar consultas a BD; si la recarga de estado falla (concurrencia), la
     * transacción lo rollback intacto.
     *
     * @param turno {@code Turno} turno a confirmar, ya persistido y cargado
     */
    @Transactional
    public void confirmarTurno(Turno turno) {
        historicoEstadoTurnoDomainService.transitionPendienteToConfirmadoTurno(turno);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.CONFIRMADO, turno));
    }

    /**
     * Vence la validación de un turno que agotó su plazo de aprobación/rechazo.
     * Libera el slot de agenda, marca el turno con motivo {@code VALIDACION_VENCIDA},
     * transiciona a {@code CANCELADO} y publica el evento de rechazo. Compone varias
     * operaciones que deben ser todo-o-nada. El turno se pasa ya cargado.
     *
     * @param turno {@code Turno} turno cuya validación vence, ya persistido y cargado
     */
    @Transactional
    public void vencerValidacionTurno(Turno turno) {
        agendaHorariosDiaDomainService.releaseAgendaHorario(turno.getAgendaHorarios());
        turno.setMotivoCancelacion(MotivoCancelacion.VALIDACION_VENCIDA);
        turnoDomainService.saveTurno(turno);
        historicoEstadoTurnoDomainService.transitionEsperaValidacionToCanceladoTurno(turno);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.NO_VALIDADO, turno));
    }

    /**
     * Marca automáticamente un turno como ausente después de que agotó su plazo de
     * anuncio tardío. Transiciona de {@code CONFIRMADO} a {@code AUSENTE} y publica
     * el evento correspondiente. El turno se pasa ya cargado.
     *
     * @param turno {@code Turno} turno a marcar como ausente, ya persistido y cargado
     */
    @Transactional
    public void marcarAusenteTurno(Turno turno) {
        historicoEstadoTurnoDomainService.transitionToAusenteTurno(turno);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.AUSENTE, turno));
    }

    /**
     * Envía un recordatorio de confirmación al paciente de un turno que aún espera
     * confirmación. No transiciona estado: solo publica el evento de notificación.
     * El turno se pasa ya cargado.
     *
     * @param turno {@code Turno} turno para recordar confirmación, ya persistido y cargado
     */
    @Transactional
    public void recordarConfirmacionTurno(Turno turno) {
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.RECORDATORIO_CONFIRMACION, turno));
    }

    //endregion

}
