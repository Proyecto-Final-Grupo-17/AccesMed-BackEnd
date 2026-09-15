package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Usuario}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Busca un usuario activo por su identificador.
     *
     * @param id {@code UUID} identificador del usuario
     * @return {@code Optional<Usuario>} el usuario si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Usuario> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Busca un usuario activo por su correo electrónico.
     *
     * @param mail {@code String} correo electrónico del usuario
     * @return {@code Optional<Usuario>} el usuario si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Usuario> findByMailAndDeletedAtIsNull(String mail);

    /**
     * Busca el usuario activo vinculado a un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @return {@code Optional<Usuario>} el usuario vinculado al médico si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Usuario> findByMedicoIdAndDeletedAtIsNull(UUID medicoId);

    /**
     * Busca el usuario activo vinculado a un admin.
     *
     * @param adminId {@code UUID} identificador del admin
     * @return {@code Optional<Usuario>} el usuario vinculado al admin si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Usuario> findByAdminIdAndDeletedAtIsNull(UUID adminId);

}
