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
 *
 * <p>El estado del turno no se cachea en la entidad: cada consulta de "turnos vivos" se
 * escribe desde {@code HistoricoEstadoTurno} (relación unidireccional), tomando el tramo
 * vigente ({@code h.fechaHoraFin IS NULL}) y filtrando {@code h.estado} contra los estados
 * finales, navegando al turno por el {@code @ManyToOne} {@code h.turno}. El índice único
 * parcial de vigencia garantiza como máximo un tramo vigente por turno.</p>
 */
@Repository
public interface TurnoRepository extends JpaRepository<Turno, UUID> {

    /**
     * Verifica si existe algún turno de la prestación cuyo estado vigente no sea final.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code boolean} {@code true} si existe al menos un turno vivo de esa prestación
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.prestacion.id = :prestacionId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    boolean existsByPrestacionIdAndEstadoVigenteNotIn(UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos de la prestación cuyo estado vigente no sea final.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos de esa prestación
     */
    @Query("SELECT COUNT(h) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.prestacion.id = :prestacionId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    long countByPrestacionIdAndEstadoVigenteNotIn(UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos de la prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(h.turno.fechaHoraInicio) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.prestacion.id = :prestacionId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByPrestacionIdAndEstadoVigenteNotIn(UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Verifica si existe algún turno con estado vigente no final cuya cobertura apunte
     * al plan indicado (vía {@code obraSocialPaciente.plan}).
     *
     * @param planId {@code UUID} identificador del plan
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code boolean} {@code true} si existe al menos un turno vivo apuntando al plan
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.obraSocialPaciente.plan.id = :planId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    boolean existsByPlanIdAndEstadoVigenteNotIn(UUID planId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos con estado vigente no final cuya cobertura apunte al plan indicado.
     *
     * @param planId {@code UUID} identificador del plan
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos apuntando al plan
     */
    @Query("SELECT COUNT(h) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.obraSocialPaciente.plan.id = :planId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    long countByPlanIdAndEstadoVigenteNotIn(UUID planId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos que cubre el plan.
     *
     * @param planId {@code UUID} identificador del plan
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(h.turno.fechaHoraInicio) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.obraSocialPaciente.plan.id = :planId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByPlanIdAndEstadoVigenteNotIn(UUID planId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos del médico cuyo estado vigente no sea final.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos de ese médico
     */
    @Query("SELECT COUNT(h) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.medico.id = :medicoId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    long countByMedicoIdAndEstadoVigenteNotIn(UUID medicoId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos del médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(h.turno.fechaHoraInicio) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.medico.id = :medicoId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByMedicoIdAndEstadoVigenteNotIn(UUID medicoId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos del par
     * médico-prestación indicado. Es el piso duro del corte de vigencia de
     * {@code MedicoPrestacion} (A1): la fecha de corte no puede ser anterior a la fecha
     * de inicio de ningún turno vivo de ese par.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(h.turno.fechaHoraInicio) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.medico.id = :medicoId AND h.turno.prestacion.id = :prestacionId "
            + "AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByMedicoIdAndPrestacionIdAndEstadoVigenteNotIn(
            UUID medicoId, UUID prestacionId, Collection<EstadoTurno> estadosFinales);

    /**
     * Cuenta los turnos del paciente cuyo estado vigente no sea final.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code long} cantidad de turnos vivos de ese paciente
     */
    @Query("SELECT COUNT(h) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.paciente.id = :pacienteId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    long countByPacienteIdAndEstadoVigenteNotIn(UUID pacienteId, Collection<EstadoTurno> estadosFinales);

    /**
     * Busca la fecha/hora de inicio más lejana entre los turnos vivos del paciente.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param estadosFinales {@code Collection<EstadoTurno>} estados finales a excluir
     * @return {@code Optional<ZonedDateTime>} la fecha máxima, vacío si no hay turnos vivos
     */
    @Query("SELECT MAX(h.turno.fechaHoraInicio) FROM HistoricoEstadoTurno h "
            + "WHERE h.turno.paciente.id = :pacienteId AND h.fechaHoraFin IS NULL AND h.estado NOT IN :estadosFinales")
    Optional<ZonedDateTime> findMaxFechaHoraInicioByPacienteIdAndEstadoVigenteNotIn(UUID pacienteId, Collection<EstadoTurno> estadosFinales);

}
