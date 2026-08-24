package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.HistoricoEstadoTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code HistoricoEstadoTurno}.
 */
@Repository
public interface HistoricoEstadoTurnoRepository extends JpaRepository<HistoricoEstadoTurno, UUID> {

    /**
     * Busca el tramo vigente (sin {@code fechaHoraFin}) de un turno.
     *
     * @param turnoId {@code UUID} identificador del turno
     * @return {@code Optional<HistoricoEstadoTurno>} el tramo vigente, si existe
     */
    Optional<HistoricoEstadoTurno> findByTurno_IdAndFechaHoraFinIsNull(UUID turnoId);

    /**
     * Busca los tramos vigentes (sin {@code fechaHoraFin}) de un conjunto de turnos.
     * Pensado para resolver el estado vigente de toda una página de turnos con una
     * única consulta (evita el N+1), armando luego un {@code Map<UUID, EstadoTurno>}.
     *
     * @param turnoIds {@code Collection<UUID>} identificadores de los turnos
     * @return {@code List<HistoricoEstadoTurno>} tramos vigentes de esos turnos
     */
    List<HistoricoEstadoTurno> findByTurno_IdInAndFechaHoraFinIsNull(Collection<UUID> turnoIds);

}
