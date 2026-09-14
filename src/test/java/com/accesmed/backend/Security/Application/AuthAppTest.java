package com.accesmed.backend.Security.Application;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Domain.PasswordResetToken;
import com.accesmed.backend.Security.Domain.RefreshToken;
import com.accesmed.backend.Security.Jwt.JwtService;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Jwt.UsuarioDetailsService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link AuthApp}: login, refresh, logout, olvidé mi contraseña
 * (siempre se comporta igual exista o no el mail) y restablecer contraseña (revoca las
 * sesiones vigentes). Todas las dependencias van mockeadas.
 */
@ExtendWith(MockitoExtension.class)
class AuthAppTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenDomainService refreshTokenDomainService;
    @Mock
    private PasswordResetTokenDomainService passwordResetTokenDomainService;
    @Mock
    private UsuarioDomainService usuarioDomainService;
    @Mock
    private UsuarioDetailsService usuarioDetailsService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;

    @InjectMocks
    private AuthApp authApp;

    private UUID usuarioId;
    private Usuario usuario;
    private UsuarioDetails usuarioDetails;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authApp, "frontendBaseUrl", "http://localhost:5173");

        usuarioId = UUID.randomUUID();
        usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setMail("medico@accesmed.local");
        usuario.setPasswordHash("hash-viejo");

        usuarioDetails = new UsuarioDetails(usuario, java.util.List.of());
    }

    @Test
    void login_credencialesValidas_devuelveAccessYRefreshToken() {
        LoginRequest loginRequest = new LoginRequest("medico@accesmed.local", "password123");

        Authentication authentication = new UsernamePasswordAuthenticationToken(usuarioDetails, null);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuario);
        when(jwtService.generateToken(usuarioDetails)).thenReturn("access-token-jwt");
        when(refreshTokenDomainService.generarYGuardarRefreshToken(usuario)).thenReturn("refresh-token-valor");

        LoginResponse loginResponse = authApp.login(loginRequest);

        assertNotNull(loginResponse);
        assertEquals("access-token-jwt", loginResponse.accessToken());
        assertEquals("refresh-token-valor", loginResponse.refreshToken());
    }

    @Test
    void refresh_refreshTokenVigente_emiteAccessTokenNuevo() {
        RefreshRequest refreshRequest = new RefreshRequest("refresh-token-valor");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);

        when(refreshTokenDomainService.findRefreshTokenVigentePorValor("refresh-token-valor"))
                .thenReturn(refreshToken);
        when(usuarioDetailsService.loadUserByUsername(usuario.getMail())).thenReturn(usuarioDetails);
        when(jwtService.generateToken(usuarioDetails)).thenReturn("access-token-nuevo");

        RefreshResponse refreshResponse = authApp.refresh(refreshRequest);

        assertNotNull(refreshResponse);
        assertEquals("access-token-nuevo", refreshResponse.accessToken());
    }

    @Test
    void logout_revocaElRefreshTokenIndicado() {
        RefreshRequest refreshRequest = new RefreshRequest("refresh-token-valor");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);

        when(refreshTokenDomainService.findRefreshTokenVigentePorValor("refresh-token-valor"))
                .thenReturn(refreshToken);

        authApp.logout(refreshRequest);

        verify(refreshTokenDomainService).revocarRefreshToken(refreshToken);
    }

    @Test
    void olvideContrasena_mailExistente_mandaMailDeRecuperacion() {
        OlvideContrasenaRequest request = new OlvideContrasenaRequest(usuario.getMail());

        when(usuarioDomainService.findUsuarioActivoByMail(usuario.getMail())).thenReturn(Optional.of(usuario));
        when(passwordResetTokenDomainService.generarYGuardarPasswordResetToken(usuario)).thenReturn("token-recuperacion");

        authApp.olvideContrasena(request);

        verify(mailService).enviarMail(eq(usuario.getMail()), anyString(), anyString());
    }

    @Test
    void olvideContrasena_mailInexistente_noMandaMailYNoLanza() {
        OlvideContrasenaRequest request = new OlvideContrasenaRequest("no-existe@accesmed.local");

        when(usuarioDomainService.findUsuarioActivoByMail("no-existe@accesmed.local")).thenReturn(Optional.empty());

        authApp.olvideContrasena(request);

        verify(mailService, never()).enviarMail(anyString(), anyString(), anyString());
    }

    @Test
    void restablecerContrasena_tokenVigente_actualizaPasswordYRevocaSesiones() {
        RestablecerContrasenaRequest request = new RestablecerContrasenaRequest("token-valido", "contrasenaNueva123");

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setUsuario(usuario);

        when(passwordResetTokenDomainService.findPasswordResetTokenVigentePorValor("token-valido"))
                .thenReturn(passwordResetToken);
        when(passwordEncoder.encode("contrasenaNueva123")).thenReturn("hash-nuevo");

        authApp.restablecerContrasena(request);

        assertEquals("hash-nuevo", usuario.getPasswordHash());
        verify(usuarioDomainService).saveUsuario(usuario);
        verify(passwordResetTokenDomainService).marcarComoUsado(passwordResetToken);
        verify(refreshTokenDomainService).revocarTodosLosRefreshTokensDeUsuario(usuario);
    }

}
