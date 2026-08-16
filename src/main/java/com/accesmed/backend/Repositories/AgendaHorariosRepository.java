package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.AgendaHorarios;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code AgendaHorarios}. Nació de solo
 * lectura para el enforcement de la precondición restrictiva de baja de Prestación contra
 * horarios futuros ocupados; la Fase B lo extiende con el resto de consultas y con
 * {@link JpaSpecificationExecutor} para el filtrado dinámico de los listados de Agenda.
 */
@Repository
public interface AgendaHorariosRepository extends JpaRepository<AgendaHorarios, UUID>,
        JpaSpecificationExecutor<AgendaHorarios> {

    /**
     * Verifica si existe un horario ocupado y activo, de fecha futura (o de hoy), de la
     * prestación indicada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @return {@code boolean} {@code true} si existe al menos un horario ocupado futuro de esa prestación
     */
    boolean existsByPrestacionIdAndEstaOcupadaTrueAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(
            UUID prestacionId, LocalDate fechaDesde);

    /**
     * Lista los horarios activos, libres (no ocupados) y de fecha futura (o de hoy) de una
     * prestación. Base de la cascada A4 (paso 2) y A6: dar de baja o recalcular slots
     * futuros libres al deshabilitar o actualizar una prestación. Los ocupados no se
     * tocan nunca.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @return {@code List<AgendaHorarios>} horarios activos, libres y futuros de esa prestación
     */
    List<AgendaHorarios> findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(
            UUID prestacionId, LocalDate fechaDesde);

    /**
     * Lista los horarios activos de un día de agenda.
     *
     * @param agendaDiaId {@code UUID} identificador del {@code AgendaDia}
     * @return {@code List<AgendaHorarios>} horarios activos de ese día
     */
    List<AgendaHorarios> findByAgendaDia_IdAndDeletedAtIsNull(UUID agendaDiaId);

    /**
     * Lista los horarios activos de un conjunto de días de agenda.
     *
     * @param agendaDiaIds {@code Collection<UUID>} identificadores de los {@code AgendaDia}
     * @return {@code List<AgendaHorarios>} horarios activos de esos días
     */
    List<AgendaHorarios> findByAgendaDia_IdInAndDeletedAtIsNull(Collection<UUID> agendaDiaIds);

    /**
     * Cuenta los horarios activos de un conjunto de días de agenda. Usado para armar los
     * conteos de {@code listAgendaMedico}.
     *
     * @param agendaDiaIds {@code Collection<UUID>} identificadores de los {@code AgendaDia}
     * @return {@code long} cantidad de horarios activos de esos días
     */
    long countByAgendaDia_IdInAndDeletedAtIsNull(Collection<UUID> agendaDiaIds);

    /**
     * Cuenta los horarios activos de toda una agenda médica (todos sus días). Usado para
     * armar los conteos de {@code listAgendaMedico}.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de horarios activos de esa agenda
     */
    long countByAgendaDia_AgendaMedico_IdAndDeletedAtIsNull(UUID agendaMedicoId);

    /**
     * Busca horarios activos por un conjunto de identificadores. Usado para resolver
     * {@code horariosAExcluir} de {@code updateAgendaMedico}.
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios
     * @return {@code List<AgendaHorarios>} horarios activos que existen entre esos identificadores
     */
    List<AgendaHorarios> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    /**
     * Cuenta cuántos, de un conjunto de horarios activos, están ocupados. Base de la
     * guarda restrictiva contra slots ocupados de {@code updateAgendaMedico} y
     * {@code updateVigenciaAgendaMedico}.
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios a revisar
     * @return {@code long} cantidad de esos horarios que están ocupados y activos
     */
    long countByIdInAndEstaOcupadaTrueAndDeletedAtIsNull(Collection<UUID> ids);

    /**
     * Busca la fecha del día de agenda más lejana entre un conjunto de horarios ocupados y
     * activos. Usado para armar el mensaje de la guarda restrictiva ("hasta qué fecha").
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios a revisar
     * @return {@code Optional<LocalDate>} la fecha máxima, vacío si ninguno está ocupado
     */
    @Query("SELECT MAX(h.agendaDia.fecha) FROM AgendaHorarios h "
            + "WHERE h.id IN :ids AND h.estaOcupada = true AND h.deletedAt IS NULL")
    Optional<LocalDate> findMaxFechaOcupadaByIdIn(@Param("ids") Collection<UUID> ids);

}
