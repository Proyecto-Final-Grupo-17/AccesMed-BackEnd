package com.accesmed.backend.Repositories;

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

}
