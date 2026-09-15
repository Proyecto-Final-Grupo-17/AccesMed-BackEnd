package com.accesmed.backend.Security.Jwt;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Intercepta cada request, valida el access token (si viene) y deja cargada la
 * identidad autenticada en el {@code SecurityContext} — con las authorities recalculadas
 * frescas, no las que hubiera en el token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    //region ========== Dependencias o inyecciones ==========

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final UsuarioDomainService usuarioDomainService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (jwtService.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID usuarioId = jwtService.extractUsuarioId(token);
                Usuario usuario = usuarioDomainService.findUsuarioActivoById(usuarioId);
                UserDetails userDetails = usuarioDetailsService.loadUserByUsername(usuario.getMail());

                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception excepcion) {
                log.debug("No se pudo autenticar el token recibido: {}", excepcion.getMessage());
            }
        }

        filterChain.doFilter(request, response);

    }

    //endregion

}
