package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.HistoricoEstadoTurno;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Repositories.HistoricoEstadoTurnoRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de dominio y persistencia para la entidad {@code HistoricoEstadoTurno}.
 * Encapsula la máquina de estados de {@code Turno}, tocando únicamente su propio
 * repositorio. Sus métodos siempre se invocan dentro de un método {@code @Transactional}
 * del caso de uso (capa {@code Application}), que es el límite real de atomicidad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricoEstadoTurnoDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final HistoricoEstadoTurnoRepository historicoEstadoTurnoRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Obtiene el estado vigente de un turno desde su histórico: el estado del tramo
     * con {@code fechaHoraFin} vacío. Es la única fuente del estado actual; se usa
     * para alimentar los mappers en las lecturas puntuales.
     *
     * @param turnoId {@code UUID} identificador del turno
     * @return {@code EstadoTurno} estado vigente del turno
     */
    public EstadoTurno getEstadoVigente(UUID turnoId) {

        log.debug("Buscando estado vigente de turno: id={}", turnoId);

        return historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId)
                .map(HistoricoEstadoTurno::getEstado)
                .orElse(null);

    }

    /**
     * Obtiene el estado vigente de un conjunto de turnos en una única consulta,
     * armando un {@code Map<UUID, EstadoTurno>} (id de turno → estado). Pensado
     * para alimentar el estado de toda una página de turnos sin incurrir en N+1.
     *
     * @param turnoIds {@code Collection<UUID>} identificadores de los turnos
     * @return {@code Map<UUID, EstadoTurno>} estado vigente por id de turno
     */
    public Map<UUID, EstadoTurno> getEstadosVigentes(Collection<UUID> turnoIds) {

        if (turnoIds.isEmpty()) {
            return Map.of();
        }

        log.debug("Buscando estados vigentes de {} turno(s)", turnoIds.size());

        return historicoEstadoTurnoRepository.findByTurno_IdInAndFechaHoraFinIsNull(turnoIds).stream()
                .collect(Collectors.toMap(historico -> historico.getTurno().getId(), HistoricoEstadoTurno::getEstado));

    }

    /**
     * Abre el primer tramo del histórico de estados de un turno recién persistido.
     * Define el estado inicial del turno (puede ser {@code ESPERA_VALIDACION} o
     * {@code PENDIENTE}, según si la prestación requiere validación).
     *
     * @param turno {@code Turno} turno ya persistido
     * @param estadoInicial {@code EstadoTurno} estado inicial del turno
     */
    public void setEstadoInicialTurno(Turno turno, EstadoTurno estadoInicial) {

        log.debug("Abriendo tramo inicial de estado para turno: código={}, estado={}", turno.getCodigo(), estadoInicial);

        //Crear nuevo histórico de estado con el estado inicial
        HistoricoEstadoTurno historicoEstadoTurnoNuevo = new HistoricoEstadoTurno();
        historicoEstadoTurnoNuevo.setTurno(turno);
        historicoEstadoTurnoNuevo.setEstado(estadoInicial);
        historicoEstadoTurnoNuevo.setFechaHoraInicio(ZonedDateTime.now());
        historicoEstadoTurnoRepository.save(historicoEstadoTurnoNuevo);

    }

    /**
     * Transiciona un turno de {@code ESPERA_VALIDACION} a {@code PENDIENTE}
     * (aprobación de validación).
     *
     * @param turno {@code Turno} turno a transicionar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el estado vigente no es
     *         {@code ESPERA_VALIDACION}
     */
    public void transitionEsperaValidacionToPendienteTurno(Turno turno) {

        log.debug("Transición ESPERA_VALIDACION → PENDIENTE de turno: id={}", turno.getId());

        //Validar que el estado vigente sea ESPERA_VALIDACION
        EstadoTurno estadoVigente = getEstadoVigente(turno.getId());
        if (estadoVigente != EstadoTurno.ESPERA_VALIDACION) {
            log.warn("No se pudo transicionar turno {}: estado vigente {} no es ESPERA_VALIDACION", turno.getId(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "TURNO_TRANSICION_INVALIDA",
                    "El turno no está en estado ESPERA_VALIDACION");
        }

        cerrarYAbrirTramo(turno, EstadoTurno.PENDIENTE);

    }

    /**
     * Transiciona un turno de {@code ESPERA_VALIDACION} a {@code CANCELADO}
     * (rechazo de validación).
     *
     * @param turno {@code Turno} turno a transicionar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el estado vigente no es
     *         {@code ESPERA_VALIDACION}
     */
    public void transitionEsperaValidacionToCanceladoTurno(Turno turno) {

        log.debug("Transición ESPERA_VALIDACION → CANCELADO de turno: id={}", turno.getId());

        //Validar que el estado vigente sea ESPERA_VALIDACION
        EstadoTurno estadoVigente = getEstadoVigente(turno.getId());
        if (estadoVigente != EstadoTurno.ESPERA_VALIDACION) {
            log.warn("No se pudo transicionar turno {}: estado vigente {} no es ESPERA_VALIDACION", turno.getId(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "TURNO_TRANSICION_INVALIDA",
                    "El turno no está en estado ESPERA_VALIDACION");
        }

        cerrarYAbrirTramo(turno, EstadoTurno.CANCELADO);

    }

    /**
     * Transiciona un turno a {@code REPROGRAMADO} (marca el turno original como reprogramado).
     * Valida que el estado vigente sea {@code PENDIENTE} o {@code CONFIRMADO}.
     *
     * @param turno {@code Turno} turno a transicionar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el estado vigente no es
     *         {@code PENDIENTE} ni {@code CONFIRMADO}
     */
    public void transitionToReprogramadoTurno(Turno turno) {

        log.debug("Transición → REPROGRAMADO de turno: id={}", turno.getId());

        //Validar que el estado vigente sea PENDIENTE o CONFIRMADO
        EstadoTurno estadoVigente = getEstadoVigente(turno.getId());
        if (estadoVigente != EstadoTurno.PENDIENTE && estadoVigente != EstadoTurno.CONFIRMADO) {
            log.warn("No se pudo transicionar turno {}: estado vigente {} no es PENDIENTE ni CONFIRMADO", turno.getId(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "TURNO_TRANSICION_INVALIDA",
                    "El turno no está en estado PENDIENTE ni CONFIRMADO");
        }

        cerrarYAbrirTramo(turno, EstadoTurno.REPROGRAMADO);

    }

    /**
     * Transiciona un turno a {@code CANCELADO}. Valida que el estado vigente no sea
     * un estado final.
     *
     * @param turno {@code Turno} turno a transicionar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el estado vigente
     *         es un estado final
     */
    public void transitionToCanceladoTurno(Turno turno) {

        log.debug("Transición → CANCELADO de turno: id={}", turno.getId());

        //Validar que el estado vigente no sea un estado final
        EstadoTurno estadoVigente = getEstadoVigente(turno.getId());
        if (estadoVigente.esFinal()) {
            log.warn("No se pudo transicionar turno {}: estado vigente {} es final", turno.getId(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "TURNO_TRANSICION_INVALIDA",
                    "El turno está en un estado final y no puede ser cancelado");
        }

        cerrarYAbrirTramo(turno, EstadoTurno.CANCELADO);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Cierra el tramo vigente de un turno y abre uno nuevo con el estado indicado.
     * Es la plomería común para todas las transiciones de estado.
     *
     * @param turno {@code Turno} turno cuyo estado va a cambiar
     * @param estadoNuevo {@code EstadoTurno} estado al que transicionar
     */
    private void cerrarYAbrirTramo(Turno turno, EstadoTurno estadoNuevo) {

        ZonedDateTime ahora = ZonedDateTime.now();

        //Cerrar el tramo vigente
        historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turno.getId())
                .ifPresent(vigente -> {
                    vigente.setFechaHoraFin(ahora);
                    //saveAndFlush: sin forzar, Hibernate agrupa INSERT antes que UPDATE al flush,
                    //así que el tramo nuevo se insertaría antes de cerrar el vigente
                    historicoEstadoTurnoRepository.saveAndFlush(vigente);
                });

        //Abrir el tramo nuevo
        HistoricoEstadoTurno historicoEstadoTurnoNuevo = new HistoricoEstadoTurno();
        historicoEstadoTurnoNuevo.setTurno(turno);
        historicoEstadoTurnoNuevo.setEstado(estadoNuevo);
        historicoEstadoTurnoNuevo.setFechaHoraInicio(ahora);
        historicoEstadoTurnoRepository.save(historicoEstadoTurnoNuevo);

    }

    //endregion

}
