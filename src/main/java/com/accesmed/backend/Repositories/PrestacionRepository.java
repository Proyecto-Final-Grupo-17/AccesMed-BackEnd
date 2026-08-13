package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Prestacion}.
 * Prestacion se retira por estados, no por baja lógica: la unicidad y los filtros de
 * "activa" se resuelven contra el <b>estado vigente del histórico</b> (tramo de
 * {@code HistoricoEstadoPrestacion} con {@code fechaHoraFin} vacío), no contra una columna
 * cacheada ni contra {@code deletedAt}. La relación con el histórico es unidireccional, así
 * que estas consultas se escriben desde {@code HistoricoEstadoPrestacion} navegando por su
 * {@code @ManyToOne} {@code h.prestacion}. Extiende {@code JpaSpecificationExecutor} para el
 * filtrado dinámico de {@code PrestacionQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface PrestacionRepository extends JpaRepository<Prestacion, UUID>, JpaSpecificationExecutor<Prestacion> {

    /**
     * Verifica si existe una prestación con el código especificado cuyo estado vigente no
     * sea el excluido (típicamente {@code DESHABILITADA}), joineando al tramo vigente del
     * histórico.
     *
     * @param codigo {@code String} código a verificar
     * @param estado {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @return {@code boolean} {@code true} si existe una prestación no deshabilitada con ese código,
     *         {@code false} en caso contrario
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPrestacion h "
            + "WHERE h.prestacion.codigo = :codigo AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    boolean existsByCodigoAndEstadoVigenteNot(String codigo, EstadoPrestacion estado);

    /**
     * Verifica si existe una prestación con el nombre especificado cuyo estado vigente no
     * sea el excluido (típicamente {@code DESHABILITADA}), joineando al tramo vigente del
     * histórico.
     *
     * @param nombre {@code String} nombre a verificar
     * @param estado {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @return {@code boolean} {@code true} si existe una prestación no deshabilitada con ese nombre,
     *         {@code false} en caso contrario
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPrestacion h "
            + "WHERE h.prestacion.nombre = :nombre AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    boolean existsByNombreAndEstadoVigenteNot(String nombre, EstadoPrestacion estado);

    /**
     * Verifica si existe una prestación con el nombre especificado cuyo estado vigente no
     * sea el excluido, excluyendo un id concreto (útil para validar unicidad al actualizar).
     *
     * @param nombre {@code String} nombre a verificar
     * @param estado {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra prestación no deshabilitada con ese nombre,
     *         {@code false} en caso contrario
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPrestacion h "
            + "WHERE h.prestacion.nombre = :nombre AND h.fechaHoraFin IS NULL AND h.estado <> :estado AND h.prestacion.id <> :id")
    boolean existsByNombreAndEstadoVigenteNotAndIdNot(String nombre, EstadoPrestacion estado, UUID id);

    /**
     * Busca una prestación por su identificador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code Optional<Prestacion>} la prestación si existe
     */
    Optional<Prestacion> findById(UUID id);

    /**
     * Busca una prestación cuyo estado vigente no sea el excluido (típicamente
     * {@code DESHABILITADA}) por su identificador, joineando al tramo vigente del
     * histórico. Deshabilitada es terminal e irreversible: una prestación en ese estado no
     * admite más cambios.
     *
     * @param id {@code UUID} identificador de la prestación
     * @param estado {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @return {@code Optional<Prestacion>} la prestación si existe y no está deshabilitada
     */
    @Query("SELECT h.prestacion FROM HistoricoEstadoPrestacion h "
            + "WHERE h.prestacion.id = :id AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    Optional<Prestacion> findByIdAndEstadoVigenteNot(UUID id, EstadoPrestacion estado);

    /**
     * Lista todas las prestaciones.
     *
     * @return {@code List<Prestacion>} lista de todas las prestaciones
     */
    List<Prestacion> findAll();

    /**
     * Verifica si existe alguna prestación de la especialidad indicada cuyo estado vigente
     * no sea el excluido (típicamente {@code DESHABILITADA}), joineando al tramo vigente
     * del histórico. Usada por la baja restrictiva de {@code Especialidad}.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @param estado {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @return {@code boolean} {@code true} si existe al menos una prestación no deshabilitada de esa especialidad
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEstadoPrestacion h "
            + "WHERE h.prestacion.especialidad.id = :especialidadId AND h.fechaHoraFin IS NULL AND h.estado <> :estado")
    boolean existsByEspecialidadIdAndEstadoVigenteNot(UUID especialidadId, EstadoPrestacion estado);

}
