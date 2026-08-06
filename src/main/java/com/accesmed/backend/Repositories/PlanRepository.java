package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Plan}.
 * Plan se retira por estados, no por baja lógica: la unicidad (por obra social) y los
 * filtros de "activo" se resuelven contra {@code estadoActual}. Extiende
 * {@code JpaSpecificationExecutor} para el filtrado dinámico de {@code PlanQueryService}
 * (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID>, JpaSpecificationExecutor<Plan> {

    /**
     * Busca un plan por su identificador.
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code Optional<Plan>} el plan si existe
     */
    Optional<Plan> findById(UUID id);

    /**
     * Busca un plan activo (no deshabilitado) por su identificador. Deshabilitado es
     * terminal e irreversible: un plan en ese estado no admite más cambios.
     *
     * @param id {@code UUID} identificador del plan
     * @param estadoActual {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code Optional<Plan>} el plan si existe y no está deshabilitado
     */
    Optional<Plan> findByIdAndEstadoActualNot(UUID id, EstadoPlan estadoActual);

    /**
     * Lista todos los planes de una obra social determinada.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<Plan>} lista de planes de esa obra social
     */
    List<Plan> findAllByObraSocialId(UUID obraSocialId);

    /**
     * Lista todos los planes no deshabilitados de una obra social determinada. Usada por
     * la baja restrictiva/cascada de {@code ObraSocial}.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param estadoActual {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code List<Plan>} lista de planes no deshabilitados de esa obra social
     */
    List<Plan> findAllByObraSocialIdAndEstadoActualNot(UUID obraSocialId, EstadoPlan estadoActual);

    /**
     * Verifica si existe un plan no deshabilitado con el código especificado dentro de
     * una obra social.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param codigo {@code String} código a verificar
     * @param estadoActual {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code boolean} {@code true} si existe un plan no deshabilitado con ese código
     */
    boolean existsByObraSocialIdAndCodigoAndEstadoActualNot(UUID obraSocialId, String codigo, EstadoPlan estadoActual);

    /**
     * Verifica si existe un plan no deshabilitado con el nombre especificado dentro de
     * una obra social.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param nombre {@code String} nombre a verificar
     * @param estadoActual {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code boolean} {@code true} si existe un plan no deshabilitado con ese nombre
     */
    boolean existsByObraSocialIdAndNombreAndEstadoActualNot(UUID obraSocialId, String nombre, EstadoPlan estadoActual);

    /**
     * Verifica si existe un plan no deshabilitado con el nombre especificado dentro de
     * una obra social, excluyendo un id concreto.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param nombre {@code String} nombre a verificar
     * @param estadoActual {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro plan no deshabilitado con ese nombre
     */
    boolean existsByObraSocialIdAndNombreAndEstadoActualNotAndIdNot(UUID obraSocialId, String nombre, EstadoPlan estadoActual, UUID id);

}
