package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Especialidad}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface EspecialidadRepository extends JpaRepository<Especialidad, UUID> {

    /**
     * Busca una especialidad activa por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code Optional<Especialidad>} la especialidad si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Especialidad> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Lista todas las especialidades activas.
     *
     * @return {@code List<Especialidad>} lista de especialidades activas
     */
    List<Especialidad> findAllByDeletedAtIsNull();

    /**
     * Verifica si existe una especialidad activa con el código especificado.
     *
     * @param codigo {@code String} código a verificar
     * @return {@code boolean} {@code true} si existe una especialidad activa con ese código
     */
    boolean existsByCodigoAndDeletedAtIsNull(String codigo);

    /**
     * Verifica si existe una especialidad activa con el código especificado, excluyendo
     * un id concreto (útil para validar unicidad al actualizar).
     *
     * @param codigo {@code String} código a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra especialidad activa con ese código
     */
    boolean existsByCodigoAndDeletedAtIsNullAndIdNot(String codigo, UUID id);

    /**
     * Verifica si existe una especialidad activa con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe una especialidad activa con ese nombre
     */
    boolean existsByNombreAndDeletedAtIsNull(String nombre);

    /**
     * Verifica si existe una especialidad activa con el nombre especificado, excluyendo
     * un id concreto (útil para validar unicidad al actualizar).
     *
     * @param nombre {@code String} nombre a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra especialidad activa con ese nombre
     */
    boolean existsByNombreAndDeletedAtIsNullAndIdNot(String nombre, UUID id);

}
