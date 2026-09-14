package com.accesmed.backend.Security.Jwt;

import com.accesmed.backend.Controllers.Errors.AccesMedError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Punto de entrada de autenticación de Spring Security: se dispara cuando un request sin
 * token, con un token inválido o vencido llega a un endpoint protegido. Sin este bean,
 * Spring Security responde con su 403 por defecto y sin cuerpo (no hay {@code formLogin}
 * ni {@code httpBasic} configurado, así que no resuelve un entry point propio), rompiendo
 * la promesa de "todo error llega con la misma forma {@code AccesMedError}" — acá se
 * cierra ese caso con un 401 en el mismo contrato que usa {@code GlobalExceptionHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    //region ========== Dependencias o inyecciones ==========

    private final ObjectMapper objectMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Escribe el {@code AccesMedError} de "no autenticado" con status 401.
     *
     * @param request {@code HttpServletRequest} request que disparó el rechazo
     * @param response {@code HttpServletResponse} response a completar
     * @param authenticationException {@code AuthenticationException} excepción de Spring Security
     * @throws IOException {@code IOException} si falla la escritura de la respuesta
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {

        log.warn("Request sin autenticar en {}: {}", request.getRequestURI(), authenticationException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        AccesMedError accesMedError = AccesMedError.of(HttpStatus.UNAUTHORIZED.value(), "NO_AUTENTICADO",
                "No se pudo autenticar la solicitud: falta el token, es inválido o venció.",
                List.of("No se pudo autenticar la solicitud: falta el token, es inválido o venció."),
                request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), accesMedError);

    }

    //endregion

}
