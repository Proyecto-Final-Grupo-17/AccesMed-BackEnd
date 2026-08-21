package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.AgendaHorariosDia;
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
 * Repositorio de acceso a datos para la entidad {@code AgendaHorariosDia}. Nació de solo
 * lectura para el enforcement de la precondición restrictiva de baja de Prestación contra
 * horarios futuros ocupados; la Fase B lo extiende con el resto de consultas y con
 * {@link JpaSpecificationExecutor} para el filtrado dinámico de los listados de Agenda.
 */
@Repository
public interface AgendaHorariosDiaRepository extends JpaRepository<AgendaHorariosDia, UUID>,
        JpaSpecificationExecutor<AgendaHorariosDia> {

    /**
     * Verifica si existe un horario ocupado y activo, de fecha futura (o de hoy), de la
     * prestación indicada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @return {@code boolean} {@code true} si existe al menos un horario ocupado futuro de esa prestación
     */
    boolean existsByPrestacion_IdAndEstaOcupadaTrueAndDeletedAtIsNullAndFechaGreaterThanEqual(
            UUID prestacionId, LocalDate fechaDesde);

    /**
     * Lista los horarios activos, libres (no ocupados) y de fecha futura (o de hoy) de una
     * prestación. Base de la cascada A4 (paso 2) y A6: dar de baja o recalcular slots
     * futuros libres al deshabilitar o actualizar una prestación. Los ocupados no se
     * tocan nunca.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @return {@code List<AgendaHorariosDia>} horarios activos, libres y futuros de esa prestación
     */
    List<AgendaHorariosDia> findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndFechaGreaterThanEqual(
            UUID prestacionId, LocalDate fechaDesde);

    /**
     * Busca el horario activo de una agenda para una fecha y hora dados (uso puntual, no
     * usado por el delta de composición actual pero expuesto para simetría con el resto de
     * finders por {@code agendaMedico + fecha}).
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fecha {@code LocalDate} fecha del horario
     * @return {@code Optional<AgendaHorariosDia>} el horario activo si existe
     */
    Optional<AgendaHorariosDia> findByAgendaMedico_IdAndFechaAndDeletedAtIsNull(UUID agendaMedicoId, LocalDate fecha);

    /**
     * Lista los horarios activos de una agenda completa. Base de {@code getAgendaMedico}:
     * la agenda expandida con todos sus días y horarios activos.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code List<AgendaHorariosDia>} horarios activos de esa agenda
     */
    List<AgendaHorariosDia> findByAgendaMedico_IdAndDeletedAtIsNull(UUID agendaMedicoId);

    /**
     * Lista los horarios activos de una agenda para un conjunto de fechas. Base de
     * {@code updateAgendaMedico} (resolución de {@code fechasAExcluir}) y de
     * {@code aplicarHorariosAAgregar} (rangos ya ocupados de las fechas a agregar).
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fechas {@code Collection<LocalDate>} fechas a buscar
     * @return {@code List<AgendaHorariosDia>} horarios activos de esa agenda en esas fechas
     */
    List<AgendaHorariosDia> findByAgendaMedico_IdAndFechaInAndDeletedAtIsNull(UUID agendaMedicoId, Collection<LocalDate> fechas);

    /**
     * Lista los horarios activos de una agenda posteriores a una fecha dada (excluida).
     * Usado por {@code updateVigenciaAgendaMedico} al adelantar el fin de vigencia.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fecha {@code LocalDate} fecha a partir de la cual (exclusive) se consideran "posteriores"
     * @return {@code List<AgendaHorariosDia>} horarios activos posteriores a esa fecha
     */
    List<AgendaHorariosDia> findByAgendaMedico_IdAndFechaGreaterThanAndDeletedAtIsNull(UUID agendaMedicoId, LocalDate fecha);

    /**
     * Cuenta los horarios activos de toda una agenda médica. Usado para armar los conteos
     * de {@code listAgendaMedico}.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de horarios activos de esa agenda
     */
    long countByAgendaMedico_IdAndDeletedAtIsNull(UUID agendaMedicoId);

    /**
     * Busca horarios activos por un conjunto de identificadores. Usado para resolver
     * {@code horariosAExcluir} de {@code updateAgendaMedico}.
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios
     * @return {@code List<AgendaHorariosDia>} horarios activos que existen entre esos identificadores
     */
    List<AgendaHorariosDia> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

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
     * Busca la fecha más lejana entre un conjunto de horarios ocupados y activos. Usado
     * para armar el mensaje de la guarda restrictiva ("hasta qué fecha").
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios a revisar
     * @return {@code Optional<LocalDate>} la fecha máxima, vacío si ninguno está ocupado
     */
    @Query("SELECT MAX(h.fecha) FROM AgendaHorariosDia h "
            + "WHERE h.id IN :ids AND h.estaOcupada = true AND h.deletedAt IS NULL")
    Optional<LocalDate> findMaxFechaOcupadaByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Cuenta las fechas distintas con horarios activos de una agenda: un "día" es,
     * simplemente, una fecha distinta entre los horarios activos, no una entidad propia.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de fechas distintas con horarios activos
     */
    @Query("SELECT COUNT(DISTINCT h.fecha) FROM AgendaHorariosDia h "
            + "WHERE h.agendaMedico.id = :agendaMedicoId AND h.deletedAt IS NULL")
    long countDistinctFechasByAgendaMedico(@Param("agendaMedicoId") UUID agendaMedicoId);

    /**
     * Cuenta, en una sola consulta agrupada, las fechas distintas y los horarios activos
     * de cada agenda de un lote. Cierra el N+1 de {@code listAgendaMedico}: antes se
     * pedían ambos conteos por fila de página (Fase 3 bis #1).
     *
     * @param agendaMedicoIds {@code Collection<UUID>} identificadores de las agendas
     * @return {@code List<ConteoAgendaMedico>} una proyección por agenda con sus dos conteos
     */
    @Query("SELECT h.agendaMedico.id AS agendaMedicoId, COUNT(DISTINCT h.fecha) AS cantidadDias, COUNT(h) AS cantidadHorarios "
            + "FROM AgendaHorariosDia h WHERE h.agendaMedico.id IN :agendaMedicoIds AND h.deletedAt IS NULL "
            + "GROUP BY h.agendaMedico.id")
    List<ConteoAgendaMedico> countDiasYHorariosActivosByAgendaMedicoIds(@Param("agendaMedicoIds") Collection<UUID> agendaMedicoIds);

    /**
     * Proyección cerrada del conteo agrupado de {@link #countDiasYHorariosActivosByAgendaMedicoIds}:
     * cantidad de fechas distintas y de horarios activos de una agenda médica.
     */
    interface ConteoAgendaMedico {

        /**
         * @return {@code UUID} identificador de la agenda médica
         */
        UUID getAgendaMedicoId();

        /**
         * @return {@code long} cantidad de fechas distintas con horarios activos
         */
        long getCantidadDias();

        /**
         * @return {@code long} cantidad de horarios activos
         */
        long getCantidadHorarios();

    }

}
