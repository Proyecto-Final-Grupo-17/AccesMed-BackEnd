package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Repositories.TurnoRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Lógica de dominio de solo lectura para la entidad {@code Turno}. No construye el
 * módulo Turno (sin stack de escritura): existe para el enforcement real de las
 * precondiciones restrictivas de otros módulos (bajas de Prestación, Plan) contra
 * turnos vivos, tocando únicamente {@code TurnoRepository}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final TurnoRepository turnoRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Valida que una prestación no tenga turnos vivos (estado actual no final).
     * Precondición real de la baja restrictiva de deshabilitar una prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos de
     *         esa prestación
     */
    public void validateSinTurnosVivos(UUID prestacionId) {

        long turnosVivos = turnoRepository.countByPrestacionIdAndEstadoActualNotIn(prestacionId, EstadoTurno.FINALES);

        if (turnosVivos > 0) {
            ZonedDateTime fechaMaxima = turnoRepository
                    .findMaxFechaHoraInicioByPrestacionIdAndEstadoActualNotIn(prestacionId, EstadoTurno.FINALES)
                    .orElse(null);
            log.warn("No se pudo validar sin turnos vivos para la prestación {}: {} turno(s) vivo(s), fecha máxima {}",
                    prestacionId, turnosVivos, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CON_TURNOS_VIVOS",
                    "La prestación " + prestacionId + " tiene " + turnosVivos
                            + " turno(s) vivo(s), el más lejano el " + fechaMaxima + ". No se puede deshabilitar.");
        }

    }

    /**
     * Valida que un plan no tenga turnos vivos (estado actual no final) cubiertos por él.
     * Precondición real de la baja restrictiva de deshabilitar un plan.
     *
     * @param planId {@code UUID} identificador del plan a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos
     *         cubiertos por ese plan
     */
    public void validateSinTurnosVivosDePlan(UUID planId) {

        long turnosVivos = turnoRepository.countByObraSocialPaciente_Plan_IdAndEstadoActualNotIn(planId, EstadoTurno.FINALES);

        if (turnosVivos > 0) {
            ZonedDateTime fechaMaxima = turnoRepository
                    .findMaxFechaHoraInicioByPlanIdAndEstadoActualNotIn(planId, EstadoTurno.FINALES)
                    .orElse(null);
            log.warn("No se pudo validar sin turnos vivos para el plan {}: {} turno(s) vivo(s), fecha máxima {}",
                    planId, turnosVivos, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "PLAN_CON_TURNOS_VIVOS",
                    "El plan " + planId + " tiene " + turnosVivos + " turno(s) vivo(s), el más lejano el "
                            + fechaMaxima + ". No se puede deshabilitar.");
        }

    }

    /**
     * Valida que un médico no tenga turnos vivos (estado actual no final). Precondición
     * real de la baja restrictiva de un médico.
     *
     * @param medicoId {@code UUID} identificador del médico a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos de
     *         ese médico
     */
    public void validateSinTurnosVivosDeMedico(UUID medicoId) {

        long turnosVivos = turnoRepository.countByMedicoIdAndEstadoActualNotIn(medicoId, EstadoTurno.FINALES);

        if (turnosVivos > 0) {
            ZonedDateTime fechaMaxima = turnoRepository
                    .findMaxFechaHoraInicioByMedicoIdAndEstadoActualNotIn(medicoId, EstadoTurno.FINALES)
                    .orElse(null);
            log.warn("No se pudo validar sin turnos vivos para el médico {}: {} turno(s) vivo(s), fecha máxima {}",
                    medicoId, turnosVivos, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "MEDICO_CON_TURNOS_VIVOS",
                    "El médico " + medicoId + " tiene " + turnosVivos
                            + " turno(s) vivo(s), el más lejano el " + fechaMaxima + ". No se puede dar de baja.");
        }

    }

    /**
     * Valida que un paciente no tenga turnos vivos (estado actual no final). Precondición
     * real de la baja restrictiva de un paciente.
     *
     * @param pacienteId {@code UUID} identificador del paciente a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos de
     *         ese paciente
     */
    public void validateSinTurnosVivosDePaciente(UUID pacienteId) {

        long turnosVivos = turnoRepository.countByPacienteIdAndEstadoActualNotIn(pacienteId, EstadoTurno.FINALES);

        if (turnosVivos > 0) {
            ZonedDateTime fechaMaxima = turnoRepository
                    .findMaxFechaHoraInicioByPacienteIdAndEstadoActualNotIn(pacienteId, EstadoTurno.FINALES)
                    .orElse(null);
            log.warn("No se pudo validar sin turnos vivos para el paciente {}: {} turno(s) vivo(s), fecha máxima {}",
                    pacienteId, turnosVivos, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "PACIENTE_CON_TURNOS_VIVOS",
                    "El paciente " + pacienteId + " tiene " + turnosVivos
                            + " turno(s) vivo(s), el más lejano el " + fechaMaxima + ". No se puede dar de baja.");
        }

    }

    //endregion

}
