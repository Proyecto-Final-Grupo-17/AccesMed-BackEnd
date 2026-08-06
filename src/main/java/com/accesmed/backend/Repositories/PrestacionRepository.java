package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Prestacion}.
 * Prestacion se retira por estados, no por baja lógica: la unicidad y los filtros de
 * "activa" se resuelven contra {@code estadoActual}, no contra {@code deletedAt}. Extiende
 * {@code JpaSpecificationExecutor} para el filtrado dinámico de
 * {@code PrestacionQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface PrestacionRepository extends JpaRepository<Prestacion, UUID>, JpaSpecificationExecutor<Prestacion> {

    /**
     * Verifica si existe una prestación no deshabilitada con el código especificado.
     *
     * @param codigo {@code String} código a verificar
     * @return {@code boolean} {@code true} si existe una prestación no deshabilitada con ese código,
     *         {@code false} en caso contrario
     */
    boolean existsByCodigoAndEstadoActualNot(String codigo, EstadoPrestacion estadoActual);

    /**
     * Verifica si existe una prestación no deshabilitada con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe una prestación no deshabilitada con ese nombre,
     *         {@code false} en caso contrario
     */
    boolean existsByNombreAndEstadoActualNot(String nombre, EstadoPrestacion estadoActual);

    /**
     * Verifica si existe una prestación no deshabilitada con el nombre especificado,
     * excluyendo un id concreto (útil para validar unicidad al actualizar).
     *
     * @param nombre {@code String} nombre a verificar
     * @param estadoActual {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra prestación no deshabilitada con ese nombre,
     *         {@code false} en caso contrario
     */
    boolean existsByNombreAndEstadoActualNotAndIdNot(String nombre, EstadoPrestacion estadoActual, UUID id);

    /**
     * Busca una prestación por su identificador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code Optional<Prestacion>} la prestación si existe
     */
    Optional<Prestacion> findById(UUID id);

    /**
     * Busca una prestación activa (no deshabilitada) por su identificador. Deshabilitada
     * es terminal e irreversible: una prestación en ese estado no admite más cambios.
     *
     * @param id {@code UUID} identificador de la prestación
     * @param estadoActual {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @return {@code Optional<Prestacion>} la prestación si existe y no está deshabilitada
     */
    Optional<Prestacion> findByIdAndEstadoActualNot(UUID id, EstadoPrestacion estadoActual);

    /**
     * Lista todas las prestaciones.
     *
     * @return {@code List<Prestacion>} lista de todas las prestaciones
     */
    List<Prestacion> findAll();

    /**
     * Verifica si existe alguna prestación no deshabilitada de la especialidad indicada.
     * Usada por la baja restrictiva de {@code Especialidad}.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @param estadoActual {@code EstadoPrestacion} estado a excluir (DESHABILITADA)
     * @return {@code boolean} {@code true} si existe al menos una prestación no deshabilitada de esa especialidad
     */
    boolean existsByEspecialidadIdAndEstadoActualNot(UUID especialidadId, EstadoPrestacion estadoActual);

}
