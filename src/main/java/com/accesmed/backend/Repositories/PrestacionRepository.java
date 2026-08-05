package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Prestacion}.
 * Prestacion se retira por estados, no por baja lógica: la unicidad y los filtros de
 * "activa" se resuelven contra {@code estadoActual}, no contra {@code deletedAt}.
 */
@Repository
public interface PrestacionRepository extends JpaRepository<Prestacion, UUID> {

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
     * Lista todas las prestaciones.
     *
     * @return {@code List<Prestacion>} lista de todas las prestaciones
     */
    List<Prestacion> findAll();

    /**
     * Lista todas las prestaciones de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones de esa especialidad
     */
    List<Prestacion> findAllByEspecialidadId(UUID especialidadId);

    /**
     * Lista todas las prestaciones en un estado determinado.
     *
     * @param estadoActual {@code EstadoPrestacion} estado a filtrar
     * @return {@code List<Prestacion>} lista de prestaciones en ese estado
     */
    List<Prestacion> findAllByEstadoActual(EstadoPrestacion estadoActual);

    /**
     * Lista todas las prestaciones de una especialidad determinada en un estado dado.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @param estadoActual {@code EstadoPrestacion} estado a filtrar
     * @return {@code List<Prestacion>} lista de prestaciones de esa especialidad en ese estado
     */
    List<Prestacion> findAllByEspecialidadIdAndEstadoActual(UUID especialidadId, EstadoPrestacion estadoActual);

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
