package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Prestacion}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface PrestacionRepository extends JpaRepository<Prestacion, UUID> {

    /**
     * Verifica si existe una prestación activa con el código especificado.
     *
     * @param codigo {@code String} código a verificar
     * @return {@code boolean} {@code true} si existe una prestación activa con ese código,
     *         {@code false} en caso contrario
     */
    boolean existsByCodigoAndDeletedAtIsNull(String codigo);

    /**
     * Verifica si existe una prestación activa con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe una prestación activa con ese nombre,
     *         {@code false} en caso contrario
     */
    boolean existsByNombreAndDeletedAtIsNull(String nombre);

    /**
     * Verifica si existe una prestación activa con el nombre especificado, excluyendo
     * un id concreto (útil para validar unicidad al actualizar).
     *
     * @param nombre {@code String} nombre a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra prestación activa con ese nombre,
     *         {@code false} en caso contrario
     */
    boolean existsByNombreAndDeletedAtIsNullAndIdNot(String nombre, UUID id);

    /**
     * Busca una prestación activa por su identificador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code Optional<Prestacion>} la prestación si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Prestacion> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Lista todas las prestaciones activas.
     *
     * @return {@code List<Prestacion>} lista de prestaciones activas
     */
    List<Prestacion> findAllByDeletedAtIsNull();

    /**
     * Lista todas las prestaciones activas de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones activas de esa especialidad
     */
    List<Prestacion> findAllByEspecialidadIdAndDeletedAtIsNull(UUID especialidadId);

    /**
     * Lista todas las prestaciones activas que están habilitadas
     * ({@code fechaHabilitacion IS NOT NULL}).
     *
     * @return {@code List<Prestacion>} lista de prestaciones activas habilitadas
     */
    List<Prestacion> findAllByDeletedAtIsNullAndFechaHabilitacionIsNotNull();

    /**
     * Lista todas las prestaciones activas habilitadas de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones activas habilitadas de esa especialidad
     */
    List<Prestacion> findAllByEspecialidadIdAndDeletedAtIsNullAndFechaHabilitacionIsNotNull(UUID especialidadId);

    /**
     * Lista todas las prestaciones activas que están en borrador
     * ({@code fechaHabilitacion IS NULL}).
     *
     * @return {@code List<Prestacion>} lista de prestaciones activas en borrador
     */
    List<Prestacion> findAllByDeletedAtIsNullAndFechaHabilitacionIsNull();

    /**
     * Lista todas las prestaciones activas en borrador de una especialidad determinada
     * ({@code deletedAt IS NULL} y {@code fechaHabilitacion IS NULL}).
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones activas en borrador de esa especialidad
     */
    List<Prestacion> findAllByEspecialidadIdAndDeletedAtIsNullAndFechaHabilitacionIsNull(UUID especialidadId);

}
