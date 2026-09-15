package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Admin}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    /**
     * Busca un admin activo por su identificador.
     *
     * @param id {@code UUID} identificador del admin
     * @return {@code Optional<Admin>} el admin si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Admin> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe un admin activo con el DNI especificado.
     *
     * @param dni {@code String} DNI a verificar
     * @return {@code boolean} {@code true} si existe un admin activo con ese DNI
     */
    boolean existsByDniAndDeletedAtIsNull(String dni);

    /**
     * Verifica si existe un admin activo con el email especificado.
     *
     * @param email {@code String} email a verificar
     * @return {@code boolean} {@code true} si existe un admin activo con ese email
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);

}
