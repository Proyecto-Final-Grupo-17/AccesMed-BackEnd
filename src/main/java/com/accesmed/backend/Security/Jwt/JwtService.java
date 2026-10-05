package com.accesmed.backend.Security.Jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Genera y valida los access token JWT. El token es liviano: el único claim propio es
 * el {@code subject} (id del usuario) — nunca lleva roles ni permisos embebidos, porque
 * esos se recalculan en cada request desde la base ({@link UsuarioDetailsService}).
 */
@Component
public class JwtService {

    //region ========== Dependencias o inyecciones ==========

    @Value("${accesmed.jwt.secret}")
    private String secret;

    @Value("${accesmed.jwt.expiration-minutes}")
    private long expirationMinutes;

    //endregion

    //region ========== Métodos ==========

    /**
     * Genera un access token firmado para el usuario autenticado.
     *
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code String} el token JWT compacto
     */
    public String generateToken(UsuarioDetails usuarioDetails) {

        Instant ahora = Instant.now();

        return Jwts.builder()
                .subject(usuarioDetails.getUsuarioId().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(getSigningKey())
                .compact();

    }

    /**
     * Extrae el id de usuario del token. Asume que el token ya fue validado con
     * {@link #isTokenValid(String)}.
     *
     * @param token {@code String} el token JWT
     * @return {@code UUID} el id del usuario
     */
    public UUID extractUsuarioId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * Valida la firma y la expiración del token.
     *
     * @param token {@code String} el token JWT
     * @return {@code boolean} {@code true} si el token es válido
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException excepcion) {
            return false;
        }
    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    //endregion

}
