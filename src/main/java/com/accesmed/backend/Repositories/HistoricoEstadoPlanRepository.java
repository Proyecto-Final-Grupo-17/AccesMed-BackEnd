package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

}
