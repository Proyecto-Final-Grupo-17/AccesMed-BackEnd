package com.accesmed.backend.Security.Application;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Domain.PasswordResetToken;
import com.accesmed.backend.Security.Domain.RefreshToken;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Jwt.UsuarioDetailsService;
import com.accesmed.backend.Security.Jwt.JwtService;
import com.accesmed.backend.Security.Records.Auth.Request.LoginRequest;
import com.accesmed.backend.Security.Records.Auth.Request.OlvideContrasenaRequest;
import com.accesmed.backend.Security.Records.Auth.Request.RefreshRequest;
import com.accesmed.backend.Security.Records.Auth.Request.RestablecerContrasenaRequest;
import com.accesmed.backend.Security.Records.Auth.Response.LoginResponse;
import com.accesmed.backend.Security.Records.Auth.Response.RefreshResponse;
import com.accesmed.backend.Security.Services.DomainServices.PasswordResetTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.RefreshTokenDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.Utils.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el flujo de autenticación: login, refresco de access token, logout,
 * recuperación y restablecimiento de contraseña. El mismo mecanismo de token
 * ({@code PasswordResetToken}) sirve tanto para activar una cuenta nueva como para
 * recuperar una contraseña olvidada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApp {

    //region ========== Dependencias o inyecciones ==========

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenDomainService refreshTokenDomainService;
    private final PasswordResetTokenDomainService passwordResetTokenDomainService;
    private final UsuarioDomainService usuarioDomainService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${accesmed.frontend.base-url}")
    private String frontendBaseUrl;

    //endregion

    //region ========== Métodos ==========

    /**
     * Autentica mail+password y devuelve el par de tokens.
     *
     * @param loginRequest {@code LoginRequest} credenciales
     * @return {@code LoginResponse} access token y refresh token
     */
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {

        log.info("Intento de login: mail={}", loginRequest.mail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.mail(), loginRequest.password()));

        UsuarioDetails usuarioDetails = (UsuarioDetails) authentication.getPrincipal();
        Usuario usuario = usuarioDomainService.findUsuarioActivoById(usuarioDetails.getUsuarioId());

        String accessToken = jwtService.generateToken(usuarioDetails);
        String refreshTokenValor = refreshTokenDomainService.generarYGuardarRefreshToken(usuario);

        return new LoginResponse(accessToken, refreshTokenValor);

    }

    /**
     * Emite un access token nuevo a partir de un refresh token vigente.
     *
     * @param refreshRequest {@code RefreshRequest} el refresh token
     * @return {@code RefreshResponse} el access token nuevo
     */
    @Transactional
    public RefreshResponse refresh(RefreshRequest refreshRequest) {

        log.info("Solicitud de refresh de access token");

        RefreshToken refreshToken = refreshTokenDomainService.findRefreshTokenVigentePorValor(refreshRequest.refreshToken());
        Usuario usuario = refreshToken.getUsuario();
        UsuarioDetails usuarioDetails = (UsuarioDetails) usuarioDetailsService.loadUserByUsername(usuario.getMail());

        String accessToken = jwtService.generateToken(usuarioDetails);

        return new RefreshResponse(accessToken);

    }

    /**
     * Revoca el refresh token, cerrando la sesión.
     *
     * @param refreshRequest {@code RefreshRequest} el refresh token a revocar
     */
    @Transactional
    public void logout(RefreshRequest refreshRequest) {

        log.info("Logout solicitado");

        RefreshToken refreshToken = refreshTokenDomainService.findRefreshTokenVigentePorValor(refreshRequest.refreshToken());
        refreshTokenDomainService.revocarRefreshToken(refreshToken);

    }

    /**
     * Inicia la recuperación de contraseña. Siempre se comporta igual, exista o no el
     * mail, para no revelar qué cuentas están registradas.
     *
     * @param olvideContrasenaRequest {@code OlvideContrasenaRequest} el mail
     */
    @Transactional
    public void olvideContrasena(OlvideContrasenaRequest olvideContrasenaRequest) {

        log.info("Solicitud de recuperación de contraseña");

        usuarioDomainService.findUsuarioActivoByMail(olvideContrasenaRequest.mail())
                .ifPresent(this::enviarMailRecuperacion);

    }

    /**
     * Consume un token de activación/recuperación y establece la contraseña nueva.
     * Revoca todos los refresh tokens vigentes del usuario, para forzar a re-loguearse
     * en todos los dispositivos con la contraseña nueva.
     *
     * @param restablecerContrasenaRequest {@code RestablecerContrasenaRequest} token y contraseña nueva
     */
    @Transactional
    public void restablecerContrasena(RestablecerContrasenaRequest restablecerContrasenaRequest) {

        log.info("Restableciendo contraseña");

        PasswordResetToken passwordResetToken = passwordResetTokenDomainService
                .findPasswordResetTokenVigentePorValor(restablecerContrasenaRequest.token());

        Usuario usuario = passwordResetToken.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(restablecerContrasenaRequest.passwordNueva()));
        usuarioDomainService.saveUsuario(usuario);

        passwordResetTokenDomainService.marcarComoUsado(passwordResetToken);
        refreshTokenDomainService.revocarTodosLosRefreshTokensDeUsuario(usuario);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private void enviarMailRecuperacion(Usuario usuario) {
        String token = passwordResetTokenDomainService.generarYGuardarPasswordResetToken(usuario);
        String link = frontendBaseUrl + "/restablecer-contrasena?token=" + token;
        mailService.enviarMail(usuario.getMail(), "Recuperación de contraseña — AccesMed",
                "Ingresá a este link para definir una contraseña nueva:\n\n" + link
                        + "\n\nSi no pediste esto, ignorá este mail.");
    }

    //endregion

}
