package com.accesmed.backend.Security.Application;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
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
import com.accesmed.backend.Security.Records.Auth.Response.MeResponse;
import com.accesmed.backend.Security.Records.Auth.Response.MeRolResponse;
import com.accesmed.backend.Security.Records.Auth.Response.RefreshResponse;
import com.accesmed.backend.Security.Domain.CambioMailToken;
import com.accesmed.backend.Security.Services.DomainServices.CambioMailTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.PasswordResetTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.RefreshTokenDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
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

import java.util.List;

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
    private final CambioMailTokenDomainService cambioMailTokenDomainService;
    private final UsuarioDomainService usuarioDomainService;
    private final UsuarioRolDomainService usuarioRolDomainService;
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
     * Devuelve los datos del usuario autenticado: perfil (médico o admin vinculado) y
     * roles vigentes con sus permisos.
     *
     * @param usuarioDetails {@code UsuarioDetails} usuario autenticado
     * @return {@code MeResponse} los datos del usuario
     */
    @Transactional(readOnly = true)
    public MeResponse me(UsuarioDetails usuarioDetails) {

        log.info("Solicitud de datos del usuario autenticado: usuarioId={}", usuarioDetails.getUsuarioId());

        Usuario usuario = usuarioDomainService.findUsuarioActivoById(usuarioDetails.getUsuarioId());

        String nombre = usuario.getMedico() != null ? usuario.getMedico().getNombre() : usuario.getAdmin().getNombre();
        String apellido = usuario.getMedico() != null ? usuario.getMedico().getApellido() : usuario.getAdmin().getApellido();

        List<MeRolResponse> roles = usuarioRolDomainService.findVigentesByUsuarioId(usuario.getId()).stream()
                .map(UsuarioRol::getRol)
                .map(rol -> new MeRolResponse(rol.getId(), rol.getNombre(), rol.getPermisos()))
                .toList();

        return new MeResponse(usuario.getId(), usuario.getMail(), nombre, apellido,
                usuario.getMedico() != null ? usuario.getMedico().getId() : null,
                usuario.getAdmin() != null ? usuario.getAdmin().getId() : null,
                roles);

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

    /**
     * Consume un token de cambio de mail vigente y aplica el mail nuevo sobre el usuario.
     * Re-valida que el mail nuevo siga disponible (por si otro usuario lo tomó mientras
     * la confirmación estaba pendiente).
     *
     * @param token {@code String} token de confirmación
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el mail nuevo ya no está disponible
     */
    @Transactional
    public void confirmarCambioMail(String token) {

        log.info("Confirmando cambio de mail");

        CambioMailToken cambioMailToken = cambioMailTokenDomainService.findCambioMailTokenVigentePorValor(token);
        String mailNuevo = cambioMailToken.getMailNuevo();

        usuarioDomainService.findUsuarioActivoByMail(mailNuevo).ifPresent(otro -> {
            log.warn("Mail nuevo ya registrado por otro usuario al momento de confirmar: mailNuevo={}", mailNuevo);
            throw new ReglaNegocioException(getClass(), "MAIL_YA_REGISTRADO",
                    "El mail nuevo ya está en uso por otro usuario.");
        });

        Usuario usuario = cambioMailToken.getUsuario();
        usuario.setMail(mailNuevo);
        usuarioDomainService.saveUsuario(usuario);

        cambioMailTokenDomainService.marcarComoUsado(cambioMailToken);

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
