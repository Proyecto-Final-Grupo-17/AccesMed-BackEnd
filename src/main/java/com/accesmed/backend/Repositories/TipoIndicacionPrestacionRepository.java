package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code TipoIndicacionPrestacion}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface TipoIndicacionPrestacionRepository extends JpaRepository<TipoIndicacionPrestacion, UUID> {

    /**
     * Verifica si existe un tipo de indicación activo con el código especificado.
     *
     * @param codigo {@code String} código a verificar
     * @return {@code boolean} {@code true} si existe un tipo activo con ese código,
     *         {@code false} en caso contrario
     */
    boolean existsByCodigoAndDeletedAtIsNull(String codigo);

    /**
     * Verifica si existe un tipo de indicación activo con el código especificado,
     * excluyendo un id concreto (útil para validar unicidad al actualizar).
     *
     * @param codigo {@code String} código a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro tipo activo con ese código,
     *         {@code false} en caso contrario
     */
    boolean existsByCodigoAndDeletedAtIsNullAndIdNot(String codigo, UUID id);

    /**
     * Verifica si existe un tipo de indicación activo con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe un tipo activo con ese nombre,
     *         {@code false} en caso contrario
     */
    boolean existsByNombreAndDeletedAtIsNull(String nombre);

    /**
     * Verifica si existe un tipo de indicación activo con el nombre especificado,
     * excluyendo un id concreto (útil para validar unicidad al actualizar).
     *
     * @param nombre {@code String} nombre a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro tipo activo con ese nombre,
     *         {@code false} en caso contrario
     */
    boolean existsByNombreAndDeletedAtIsNullAndIdNot(String nombre, UUID id);

    /**
     * Busca un tipo de indicación activo por su identificador.
     *
     * @param id {@code UUID} identificador del tipo de indicación
     * @return {@code Optional<TipoIndicacionPrestacion>} el tipo si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<TipoIndicacionPrestacion> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Lista todos los tipos de indicación activos.
     *
     * @return {@code List<TipoIndicacionPrestacion>} lista de tipos activos
     */
    List<TipoIndicacionPrestacion> findAllByDeletedAtIsNull();

}
