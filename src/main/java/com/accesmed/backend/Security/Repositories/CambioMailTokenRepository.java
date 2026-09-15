package com.accesmed.backend.Security.Repositories;

import com.accesmed.backend.Security.Domain.CambioMailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code CambioMailToken}.
 */
@Repository
public interface CambioMailTokenRepository extends JpaRepository<CambioMailToken, UUID> {

    /**
     * Busca un cambio mail token por su hash y que no esté usado.
     *
     * @param tokenHash {@code String} hash del token a buscar
     * @return {@code Optional<CambioMailToken>} el cambio mail token si existe y no está usado,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<CambioMailToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

}
