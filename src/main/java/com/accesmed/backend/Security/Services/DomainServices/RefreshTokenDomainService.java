package com.accesmed.backend.Security.Services.DomainServices;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Domain.RefreshToken;
import com.accesmed.backend.Security.Repositories.RefreshTokenRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Lógica de dominio para la entidad {@code RefreshToken}.
 * Encapsula la generación, validación y revocación de tokens de refresco.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final RefreshTokenRepository refreshTokenRepository;

    //endregion

    //region ========== Constantes ==========

    private static final long EXPIRATION_DIAS = 7L;

    //endregion

    //region ========== Métodos ==========

    /**
     * Genera un refresh token de alta entropía, lo hashea, lo guarda en la base de datos
     * y devuelve el valor plano (nunca se persiste).
     *
     * @param usuario {@code Usuario} usuario propietario del token
     * @return {@code String} el token plano en Base64-URL (nunca se guarda)
     */
    public String generarYGuardarRefreshToken(Usuario usuario) {

        log.debug("Generando refresh token para usuario id={}", usuario.getId());

        byte[] bytesAleatorios = new byte[32];
        new SecureRandom().nextBytes(bytesAleatorios);
        String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(tokenPlano));
        refreshToken.setExpiresAt(Instant.now().plus(EXPIRATION_DIAS, ChronoUnit.DAYS));
        refreshToken.setUsuario(usuario);
        refreshTokenRepository.save(refreshToken);

        return tokenPlano;

    }

    /**
     * Busca un refresh token por su valor plano, lo hashea internamente, y valida que
     * no esté revocado y que no haya expirado.
     *
     * @param tokenPlano {@code String} valor plano del token (como lo recibió el cliente)
     * @return {@code RefreshToken} el token vigente
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el token
     *         no existe, está revocado o expiró
     */
    public RefreshToken findRefreshTokenVigentePorValor(String tokenPlano) {

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hashToken(tokenPlano))
                .orElseThrow(() -> {
                    log.warn("Refresh token no encontrado o revocado");
                    return new RecursoNoEncontradoException(getClass(), "REFRESH_TOKEN_NO_ENCONTRADO",
                            "La sesión venció o ya no es válida. Iniciá sesión nuevamente.");
                });

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Refresh token expirado: id={}", refreshToken.getId());
            throw new RecursoNoEncontradoException(getClass(), "REFRESH_TOKEN_EXPIRADO",
                    "La sesión venció o ya no es válida. Iniciá sesión nuevamente.");
        }

        return refreshToken;

    }

    /**
     * Marca un refresh token como revocado estableciendo {@code revokedAt} al momento
     * actual y lo persiste.
     *
     * @param refreshToken {@code RefreshToken} token a revocar
     */
    public void revocarRefreshToken(RefreshToken refreshToken) {

        log.debug("Revocando refresh token: id={}", refreshToken.getId());

        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

    }

    /**
     * Busca y revoca todos los refresh tokens vigentes de un usuario,
     * marcándolos como revocados.
     *
     * @param usuario {@code Usuario} usuario cuyos tokens se revocarán
     */
    public void revocarTodosLosRefreshTokensDeUsuario(Usuario usuario) {

        log.debug("Revocando todos los refresh tokens del usuario id={}", usuario.getId());

        var tokensVigentes = refreshTokenRepository.findByUsuarioIdAndRevokedAtIsNull(usuario.getId());
        var ahora = Instant.now();
        tokensVigentes.forEach(token -> token.setRevokedAt(ahora));
        refreshTokenRepository.saveAll(tokensVigentes);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Hashea un token con SHA-256 y devuelve la representación hexadecimal.
     *
     * @param tokenPlano {@code String} valor plano del token
     * @return {@code String} hash hexadecimal del token
     * @throws IllegalStateException {@code IllegalStateException} si SHA-256 no está disponible
     */
    private String hashToken(String tokenPlano) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(tokenPlano.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexBuilder = new StringBuilder();
            for (byte b : hashBytes) {
                hexBuilder.append(String.format("%02x", b));
            }
            return hexBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }

    }

    //endregion

}
