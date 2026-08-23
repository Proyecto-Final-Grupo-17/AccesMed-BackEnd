package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code ObraSocialPlanPrestacion}. Todas
 * las consultas excluyen las coberturas dadas de baja ({@code deletedAt IS NULL}), salvo
 * que el nombre del método indique lo contrario.
 */
@Repository
public interface ObraSocialPlanPrestacionRepository
        extends JpaRepository<ObraSocialPlanPrestacion, UUID>, JpaSpecificationExecutor<ObraSocialPlanPrestacion> {

    /**
     * Busca una cobertura activa por su identificador.
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code Optional<ObraSocialPlanPrestacion>} la cobertura activa, si existe
     */
    Optional<ObraSocialPlanPrestacion> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe una cobertura activa entre el plan y la prestación indicados.
     *
     * @param planId {@code UUID} identificador del plan
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code boolean} {@code true} si ya existe una cobertura activa entre ambos
     */
    boolean existsByPlan_IdAndPrestacion_IdAndDeletedAtIsNull(UUID planId, UUID prestacionId);

    /**
     * Busca las coberturas activas de un plan.
     *
     * @param planId {@code UUID} identificador del plan
     * @return {@code List<ObraSocialPlanPrestacion>} las coberturas activas de ese plan
     */
    List<ObraSocialPlanPrestacion> findByPlan_IdAndDeletedAtIsNull(UUID planId);

    /**
     * Busca las coberturas activas de un lote de planes, en una sola consulta. Base de la
     * resolución en lote de los responses anidados de {@code ObraSocial}/{@code Plan}
     * (evita N+1).
     *
     * @param planIds {@code Collection<UUID>} identificadores de los planes
     * @return {@code List<ObraSocialPlanPrestacion>} las coberturas activas de esos planes
     */
    List<ObraSocialPlanPrestacion> findByPlan_IdInAndDeletedAtIsNull(Collection<UUID> planIds);

    /**
     * Busca las coberturas activas de una prestación. Utilizada por la cascada de baja de
     * {@code Prestacion} (A4, paso 4).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code List<ObraSocialPlanPrestacion>} las coberturas activas de esa prestación
     */
    List<ObraSocialPlanPrestacion> findByPrestacion_IdAndDeletedAtIsNull(UUID prestacionId);

}
