package com.accesmed.backend.Security.Services.DomainServices;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Domain.PasswordResetToken;
import com.accesmed.backend.Security.Repositories.PasswordResetTokenRepository;
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

/**
 * Lógica de dominio para la entidad {@code PasswordResetToken}.
 * Encapsula la generación, validación y consumo de tokens para reset de contraseña
 * y activación de nuevas cuentas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetTokenDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    //endregion

    //region ========== Constantes ==========

    private static final long EXPIRATION_HORAS = 1L;

    //endregion

    //region ========== Métodos ==========

    /**
     * Genera un password reset token de alta entropía, lo hashea, lo guarda en la base
     * de datos y devuelve el valor plano (nunca se persiste).
     *
     * @param usuario {@code Usuario} usuario propietario del token
     * @return {@code String} el token plano en Base64-URL (nunca se guarda)
     */
    public String generarYGuardarPasswordResetToken(Usuario usuario) {

        log.debug("Generando password reset token para usuario id={}", usuario.getId());

        byte[] bytesAleatorios = new byte[32];
        new SecureRandom().nextBytes(bytesAleatorios);
        String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setTokenHash(hashToken(tokenPlano));
        passwordResetToken.setExpiresAt(Instant.now().plus(EXPIRATION_HORAS, ChronoUnit.HOURS));
        passwordResetToken.setUsuario(usuario);
        passwordResetTokenRepository.save(passwordResetToken);

        return tokenPlano;

    }

    /**
     * Busca un password reset token por su valor plano, lo hashea internamente, y valida
     * que no esté usado y que no haya expirado.
     *
     * @param tokenPlano {@code String} valor plano del token (como lo recibió el cliente)
     * @return {@code PasswordResetToken} el token vigente
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el token
     *         no existe, está usado o expiró
     */
    public PasswordResetToken findPasswordResetTokenVigentePorValor(String tokenPlano) {

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(hashToken(tokenPlano))
                .orElseThrow(() -> {
                    log.warn("Password reset token no encontrado o usado");
                    return new RecursoNoEncontradoException(getClass(), "PASSWORD_RESET_TOKEN_NO_ENCONTRADO",
                            "El enlace para restablecer la contraseña no es válido, venció o ya fue usado.");
                });

        if (passwordResetToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Password reset token expirado: id={}", passwordResetToken.getId());
            throw new RecursoNoEncontradoException(getClass(), "PASSWORD_RESET_TOKEN_EXPIRADO",
                    "El enlace para restablecer la contraseña no es válido, venció o ya fue usado.");
        }

        return passwordResetToken;

    }

    /**
     * Marca un password reset token como usado estableciendo {@code usedAt} al momento
     * actual y lo persiste.
     *
     * @param passwordResetToken {@code PasswordResetToken} token a marcar como usado
     */
    public void marcarComoUsado(PasswordResetToken passwordResetToken) {

        log.debug("Marcando password reset token como usado: id={}", passwordResetToken.getId());

        passwordResetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(passwordResetToken);

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
