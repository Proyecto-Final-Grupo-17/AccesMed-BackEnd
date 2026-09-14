package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Plan}.
 * Plan se retira por estados, no por baja lógica: la unicidad (por obra social) y los
 * filtros de "activo" se resuelven contra el <b>estado vigente del histórico</b> (tramo de
 * {@code HistoricoEstadoPlan} con {@code fechaHoraFin} vacío), no contra una columna
 * cacheada. La relación con el histórico es unidireccional, así que estas consultas se
 * escriben desde {@code HistoricoEstadoPlan} navegando por su {@code @ManyToOne}
 * {@code h.plan}. Extiende {@code JpaSpecificationExecutor} para el filtrado dinámico de
 * {@code PlanQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
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
     * Busca un plan cuyo estado vigente no sea el excluido (típicamente
     * {@code DESHABILITADO}) por su identificador, joineando al tramo vigente del
     * histórico. Deshabilitado es terminal e irreversible: un plan en ese estado no admite
     * más cambios.
     *
     * @param id {@code UUID} identificador del plan
     * @param estado {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code Optional<Plan>} el plan si existe y no está deshabilitado
     */
    @Query("SELECT h.plan FROM HistoricoEstadoPlan h "
            + "WHERE h.plan.id = :id AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    Optional<Plan> findByIdAndEstadoVigenteNot(UUID id, EstadoPlan estado);

    /**
     * Lista todos los planes de una obra social determinada.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<Plan>} lista de planes de esa obra social
     */
    List<Plan> findAllByObraSocialId(UUID obraSocialId);

    /**
     * Lista los planes de una obra social cuyo estado vigente no sea el excluido
     * (típicamente {@code DESHABILITADO}), joineando al tramo vigente del histórico. Usada
     * por la baja restrictiva/cascada de {@code ObraSocial}.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param estado {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code List<Plan>} lista de planes no deshabilitados de esa obra social
     */
    @Query("SELECT h.plan FROM HistoricoEstadoPlan h "
            + "WHERE h.plan.obraSocial.id = :obraSocialId AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    List<Plan> findAllByObraSocialIdAndEstadoVigenteNot(UUID obraSocialId, EstadoPlan estado);

    /**
     * Verifica si existe un plan con el código especificado dentro de una obra social cuyo
     * estado vigente no sea el excluido (típicamente {@code DESHABILITADO}), joineando al
     * tramo vigente del histórico.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param codigo {@code String} código a verificar
     * @param estado {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code boolean} {@code true} si existe un plan no deshabilitado con ese código
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPlan h "
            + "WHERE h.plan.obraSocial.id = :obraSocialId AND h.plan.codigo = :codigo "
            + "AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    boolean existsByObraSocialIdAndCodigoAndEstadoVigenteNot(UUID obraSocialId, String codigo, EstadoPlan estado);

    /**
     * Verifica si existe un plan con el nombre especificado dentro de una obra social cuyo
     * estado vigente no sea el excluido (típicamente {@code DESHABILITADO}), joineando al
     * tramo vigente del histórico.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param nombre {@code String} nombre a verificar
     * @param estado {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @return {@code boolean} {@code true} si existe un plan no deshabilitado con ese nombre
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPlan h "
            + "WHERE h.plan.obraSocial.id = :obraSocialId AND h.plan.nombre = :nombre "
            + "AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    boolean existsByObraSocialIdAndNombreAndEstadoVigenteNot(UUID obraSocialId, String nombre, EstadoPlan estado);

    /**
     * Verifica si existe un plan con el nombre especificado dentro de una obra social cuyo
     * estado vigente no sea el excluido, excluyendo un id concreto (útil para validar
     * unicidad al actualizar).
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param nombre {@code String} nombre a verificar
     * @param estado {@code EstadoPlan} estado a excluir (DESHABILITADO)
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro plan no deshabilitado con ese nombre
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPlan h "
            + "WHERE h.plan.obraSocial.id = :obraSocialId AND h.plan.nombre = :nombre "
            + "AND h.fechaHoraFin IS NULL AND h.estado <> :estado AND h.plan.id <> :id")
    boolean existsByObraSocialIdAndNombreAndEstadoVigenteNotAndIdNot(UUID obraSocialId, String nombre, EstadoPlan estado, UUID id);

}
