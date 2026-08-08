package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de solo lectura para la entidad {@code Turno}. No construye el módulo
 * Turno (sin stack de escritura): existe para el enforcement real de las precondiciones
 * restrictivas de otros módulos (bajas de Prestación, Plan) contra turnos vivos.
 */
@Repository
public interface TurnoRepository extends JpaRepository<Turno, UUID> {

    /**
     * Verifica si existe algún turno de la prestación con estado actual no final.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code boolean} {@code true} si existe al menos un turno vivo de esa prestación
     */
    boolean existsByPrestacionIdAndEstadoActualNotIn(UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos de la prestación con estado actual no final.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos de esa prestación
     */
    long countByPrestacionIdAndEstadoActualNotIn(UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos de la prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(t.fechaHoraInicio) FROM Turno t WHERE t.prestacion.id = :prestacionId AND t.estadoActual NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByPrestacionIdAndEstadoActualNotIn(UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Verifica si existe algún turno con estado actual no final cuya cobertura apunte
     * al plan indicado (vía {@code obraSocialPaciente.plan}).
     *
     * @param planId {@code UUID} identificador del plan
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code boolean} {@code true} si existe al menos un turno vivo apuntando al plan
     */
    boolean existsByObraSocialPaciente_Plan_IdAndEstadoActualNotIn(UUID planId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos con estado actual no final cuya cobertura apunte al plan indicado.
     *
     * @param planId {@code UUID} identificador del plan
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos apuntando al plan
     */
    long countByObraSocialPaciente_Plan_IdAndEstadoActualNotIn(UUID planId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos que cubre el plan.
     *
     * @param planId {@code UUID} identificador del plan
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(t.fechaHoraInicio) FROM Turno t WHERE t.obraSocialPaciente.plan.id = :planId AND t.estadoActual NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByPlanIdAndEstadoActualNotIn(UUID planId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos del médico con estado actual no final.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos de ese médico
     */
    long countByMedicoIdAndEstadoActualNotIn(UUID medicoId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos del médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(t.fechaHoraInicio) FROM Turno t WHERE t.medico.id = :medicoId AND t.estadoActual NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByMedicoIdAndEstadoActualNotIn(UUID medicoId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos del paciente con estado actual no final.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos de ese paciente
     */
    long countByPacienteIdAndEstadoActualNotIn(UUID pacienteId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos del paciente.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(t.fechaHoraInicio) FROM Turno t WHERE t.paciente.id = :pacienteId AND t.estadoActual NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByPacienteIdAndEstadoActualNotIn(UUID pacienteId, Collection<EstadoTurno> estadosFinales);

}
