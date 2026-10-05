package com.accesmed.backend.Security.Services.DomainServices;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Domain.CambioMailToken;
import com.accesmed.backend.Security.Repositories.CambioMailTokenRepository;
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
 * Lógica de dominio para la entidad {@code CambioMailToken}.
 * Encapsula la generación, validación y consumo de tokens para confirmar un cambio de
 * mail (propio o disparado por el SuperAdmin) contra el mail nuevo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CambioMailTokenDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final CambioMailTokenRepository cambioMailTokenRepository;

    //endregion

    //region ========== Constantes ==========

    private static final long EXPIRATION_HORAS = 1L;

    //endregion

    //region ========== Métodos ==========

    /**
     * Genera un cambio mail token de alta entropía, lo hashea, lo guarda en la base de
     * datos junto con el mail nuevo propuesto, y devuelve el valor plano (nunca se
     * persiste).
     *
     * @param usuario {@code Usuario} usuario propietario del token
     * @param mailNuevo {@code String} mail nuevo propuesto, pendiente de confirmación
     * @return {@code String} el token plano en Base64-URL (nunca se guarda)
     */
    public String generarYGuardarCambioMailToken(Usuario usuario, String mailNuevo) {

        log.debug("Generando cambio mail token para usuario id={}, mailNuevo={}", usuario.getId(), mailNuevo);

        byte[] bytesAleatorios = new byte[32];
        new SecureRandom().nextBytes(bytesAleatorios);
        String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);

        CambioMailToken cambioMailToken = new CambioMailToken();
        cambioMailToken.setTokenHash(hashToken(tokenPlano));
        cambioMailToken.setExpiresAt(Instant.now().plus(EXPIRATION_HORAS, ChronoUnit.HOURS));
        cambioMailToken.setMailNuevo(mailNuevo);
        cambioMailToken.setUsuario(usuario);
        cambioMailTokenRepository.save(cambioMailToken);

        return tokenPlano;

    }

    /**
     * Busca un cambio mail token por su valor plano, lo hashea internamente, y valida
     * que no esté usado y que no haya expirado.
     *
     * @param tokenPlano {@code String} valor plano del token (como lo recibió el cliente)
     * @return {@code CambioMailToken} el token vigente
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el token
     *         no existe, está usado o expiró
     */
    public CambioMailToken findCambioMailTokenVigentePorValor(String tokenPlano) {

        CambioMailToken cambioMailToken = cambioMailTokenRepository.findByTokenHashAndUsedAtIsNull(hashToken(tokenPlano))
                .orElseThrow(() -> {
                    log.warn("Cambio mail token no encontrado o usado");
                    return new RecursoNoEncontradoException(getClass(), "CAMBIO_MAIL_TOKEN_NO_ENCONTRADO",
                            "El enlace para confirmar el cambio de mail no es válido, venció o ya fue usado.");
                });

        if (cambioMailToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Cambio mail token expirado: id={}", cambioMailToken.getId());
            throw new RecursoNoEncontradoException(getClass(), "CAMBIO_MAIL_TOKEN_EXPIRADO",
                    "El enlace para confirmar el cambio de mail no es válido, venció o ya fue usado.");
        }

        return cambioMailToken;

    }

    /**
     * Marca un cambio mail token como usado estableciendo {@code usedAt} al momento
     * actual y lo persiste.
     *
     * @param cambioMailToken {@code CambioMailToken} token a marcar como usado
     */
    public void marcarComoUsado(CambioMailToken cambioMailToken) {

        log.debug("Marcando cambio mail token como usado: id={}", cambioMailToken.getId());

        cambioMailToken.setUsedAt(Instant.now());
        cambioMailTokenRepository.save(cambioMailToken);

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
