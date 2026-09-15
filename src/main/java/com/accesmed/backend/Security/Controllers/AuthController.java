package com.accesmed.backend.Security.Controllers;

import com.accesmed.backend.Security.Application.AuthApp;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Records.Auth.Request.LoginRequest;
import com.accesmed.backend.Security.Records.Auth.Request.OlvideContrasenaRequest;
import com.accesmed.backend.Security.Records.Auth.Request.RefreshRequest;
import com.accesmed.backend.Security.Records.Auth.Request.RestablecerContrasenaRequest;
import com.accesmed.backend.Security.Records.Auth.Response.LoginResponse;
import com.accesmed.backend.Security.Records.Auth.Response.MeResponse;
import com.accesmed.backend.Security.Records.Auth.Response.RefreshResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de entrada del flujo de autenticación: login, refresh, logout, recuperación y
 * restablecimiento de contraseña.
 */
@Slf4j
@RestController
@RequestMapping("/accesmed-api/Auth")
@RequiredArgsConstructor
public class AuthController {

    //region ========== Dependencias o inyecciones ==========

    private final AuthApp authApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Autentica y devuelve el access token y el refresh token.
     *
     * @param loginRequest {@code LoginRequest} credenciales
     * @return {@code ResponseEntity<LoginResponse>} los tokens (200)
     */
    @PostMapping("/Login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Solicitud recibida: login mail={}", loginRequest.mail());
        return ResponseEntity.ok(authApp.login(loginRequest));
    }

    /**
     * Devuelve los datos del usuario autenticado: perfil y roles vigentes con sus permisos.
     *
     * @param usuarioDetails {@code UsuarioDetails} usuario autenticado
     * @return {@code ResponseEntity<MeResponse>} los datos del usuario (200)
     */
    @GetMapping("/Me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UsuarioDetails usuarioDetails) {
        log.info("Solicitud recibida: datos del usuario autenticado");
        return ResponseEntity.ok(authApp.me(usuarioDetails));
    }

    /**
     * Emite un access token nuevo a partir de un refresh token vigente.
     *
     * @param refreshRequest {@code RefreshRequest} el refresh token
     * @return {@code ResponseEntity<RefreshResponse>} el access token nuevo (200)
     */
    @PostMapping("/Refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        log.info("Solicitud recibida: refresh de access token");
        return ResponseEntity.ok(authApp.refresh(refreshRequest));
    }

    /**
     * Cierra la sesión revocando el refresh token.
     *
     * @param refreshRequest {@code RefreshRequest} el refresh token a revocar
     * @return {@code ResponseEntity<Void>} sin contenido (204)
     */
    @PostMapping("/Logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest refreshRequest) {
        log.info("Solicitud recibida: logout");
        authApp.logout(refreshRequest);
        return ResponseEntity.noContent().build();
    }

    /**
     * Inicia la recuperación de contraseña. Siempre responde 200, exista o no el mail.
     *
     * @param olvideContrasenaRequest {@code OlvideContrasenaRequest} el mail
     * @return {@code ResponseEntity<Void>} 200 siempre
     */
    @PostMapping("/OlvideContrasena")
    public ResponseEntity<Void> olvideContrasena(@Valid @RequestBody OlvideContrasenaRequest olvideContrasenaRequest) {
        log.info("Solicitud recibida: olvidé mi contraseña");
        authApp.olvideContrasena(olvideContrasenaRequest);
        return ResponseEntity.ok().build();
    }

    /**
     * Consume un token de activación/recuperación y establece la contraseña nueva.
     *
     * @param restablecerContrasenaRequest {@code RestablecerContrasenaRequest} token y contraseña nueva
     * @return {@code ResponseEntity<Void>} 200
     */
    @PostMapping("/RestablecerContrasena")
    public ResponseEntity<Void> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaRequest restablecerContrasenaRequest) {
        log.info("Solicitud recibida: restablecer contraseña");
        authApp.restablecerContrasena(restablecerContrasenaRequest);
        return ResponseEntity.ok().build();
    }

    //endregion

}
