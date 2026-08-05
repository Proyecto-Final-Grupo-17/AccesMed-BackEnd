package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Repositories.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Plan}.
 * Solo contiene métodos de búsqueda y listado, sin lógica de modificación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final PlanRepository planRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Lista todos los planes de una obra social determinada.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<Plan>} lista de planes de esa obra social
     */
    public List<Plan> findPlanesByObraSocial(UUID obraSocialId) {

        log.debug("Listando planes de obra social: {}", obraSocialId);

        return planRepository.findAllByObraSocialId(obraSocialId);

    }

    /**
     * Lista los planes no deshabilitados de una obra social determinada. Usada por la
     * baja restrictiva/cascada de {@code ObraSocial}.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<Plan>} lista de planes no deshabilitados de esa obra social
     */
    public List<Plan> findPlanesNoDeshabilitadosByObraSocial(UUID obraSocialId) {

        log.debug("Listando planes no deshabilitados de obra social: {}", obraSocialId);

        return planRepository.findAllByObraSocialIdAndEstadoActualNot(obraSocialId, EstadoPlan.DESHABILITADO);

    }

    //endregion

}
