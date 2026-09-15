package com.accesmed.backend.Security.Repositories;

import com.accesmed.backend.Security.Domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code RefreshToken}.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Busca un refresh token por su hash y que no esté revocado.
     *
     * @param tokenHash {@code String} hash del token a buscar
     * @return {@code Optional<RefreshToken>} el refresh token si existe y no está revocado,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    /**
     * Busca todos los refresh tokens vigentes (no revocados) de un usuario.
     *
     * @param usuarioId {@code UUID} identificador del usuario
     * @return {@code List<RefreshToken>} los refresh tokens del usuario que no están revocados
     */
    List<RefreshToken> findByUsuarioIdAndRevokedAtIsNull(UUID usuarioId);

}
