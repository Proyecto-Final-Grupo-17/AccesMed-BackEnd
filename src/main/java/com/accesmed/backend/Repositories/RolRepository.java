package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Rol}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface RolRepository extends JpaRepository<Rol, UUID> {

    /**
     * Busca un rol activo por su identificador.
     *
     * @param id {@code UUID} identificador del rol
     * @return {@code Optional<Rol>} el rol si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Rol> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Busca un rol activo por su nombre.
     *
     * @param nombre {@code String} nombre del rol
     * @return {@code Optional<Rol>} el rol si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Rol> findByNombreAndDeletedAtIsNull(String nombre);

    /**
     * Verifica si existe un rol activo con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe un rol activo con ese nombre
     */
    boolean existsByNombreAndDeletedAtIsNull(String nombre);

}
