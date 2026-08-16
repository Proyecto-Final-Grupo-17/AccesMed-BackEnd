package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.AgendaDia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code AgendaDia}.
 */
@Repository
public interface AgendaDiaRepository extends JpaRepository<AgendaDia, UUID> {

    /**
     * Busca el día activo de una agenda para una fecha dada. Base del get-or-create de
     * {@code updateAgendaMedico}: si no existe, el {@code App} crea uno nuevo.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fecha {@code LocalDate} fecha del día
     * @return {@code Optional<AgendaDia>} el día activo si existe
     */
    Optional<AgendaDia> findByAgendaMedico_IdAndFechaAndDeletedAtIsNull(UUID agendaMedicoId, LocalDate fecha);

    /**
     * Lista los días activos de una agenda.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code List<AgendaDia>} días activos de esa agenda
     */
    List<AgendaDia> findByAgendaMedico_IdAndDeletedAtIsNull(UUID agendaMedicoId);

    /**
     * Cuenta los días activos de una agenda.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de días activos
     */
    long countByAgendaMedico_IdAndDeletedAtIsNull(UUID agendaMedicoId);

    /**
     * Busca días activos de una agenda posteriores a una fecha dada (excluida). Usado por
     * {@code updateVigenciaAgendaMedico} al adelantar el fin de vigencia.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fecha {@code LocalDate} fecha a partir de la cual (exclusive) se consideran "posteriores"
     * @return {@code List<AgendaDia>} días activos posteriores a esa fecha
     */
    List<AgendaDia> findByAgendaMedico_IdAndFechaGreaterThanAndDeletedAtIsNull(UUID agendaMedicoId, LocalDate fecha);

    /**
     * Busca días activos por un conjunto de identificadores. Usado para resolver
     * {@code diasAExcluir} de {@code updateAgendaMedico}.
     *
     * @param ids {@code Collection<UUID>} identificadores de los días
     * @return {@code List<AgendaDia>} días activos que existen entre esos identificadores
     */
    List<AgendaDia> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

}
