package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code HistoricoEstadoPlan}.
 */
@Repository
public interface HistoricoEstadoPlanRepository extends JpaRepository<HistoricoEstadoPlan, UUID> {

    /**
     * Busca el tramo vigente (sin {@code fechaHoraFin}) de un plan.
     *
     * @param planId {@code UUID} identificador del plan
     * @return {@code Optional<HistoricoEstadoPlan>} el tramo vigente, si existe
     */
    Optional<HistoricoEstadoPlan> findByPlanIdAndFechaHoraFinIsNull(UUID planId);

    /**
     * Busca el tramo vigente (sin {@code fechaHoraFin}) de un plan, excluyendo un estado
     * puntual. Útil para encontrar el tramo activo que no sea {@code DESHABILITADO}
     * (transición terminal, sin tramo posterior).
     *
     * @param planId {@code UUID} identificador del plan
     * @param estado {@code EstadoPlan} estado a excluir de la búsqueda
     * @return {@code Optional<HistoricoEstadoPlan>} el tramo vigente que no está en ese
     *         estado, si existe
     */
    Optional<HistoricoEstadoPlan> findByPlanIdAndFechaHoraFinIsNullAndEstadoNot(UUID planId, EstadoPlan estado);

    /**
     * Busca los tramos vigentes (sin {@code fechaHoraFin}) de un conjunto de planes.
     * Pensado para resolver el estado vigente de toda una página de planes (o de los
     * planes anidados de una obra social) con una única consulta (evita el N+1), armando
     * luego un {@code Map<UUID, EstadoPlan>}.
     *
     * @param planIds {@code Collection<UUID>} identificadores de los planes
     * @return {@code List<HistoricoEstadoPlan>} tramos vigentes de esos planes
     */
    List<HistoricoEstadoPlan> findByPlanIdInAndFechaHoraFinIsNull(Collection<UUID> planIds);

}
