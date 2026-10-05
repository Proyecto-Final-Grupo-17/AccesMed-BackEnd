package com.accesmed.backend.Security.Repositories;

import com.accesmed.backend.Security.Domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code PasswordResetToken}.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Busca un password reset token por su hash y que no esté usado.
     *
     * @param tokenHash {@code String} hash del token a buscar
     * @return {@code Optional<PasswordResetToken>} el password reset token si existe y no está usado,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

}
