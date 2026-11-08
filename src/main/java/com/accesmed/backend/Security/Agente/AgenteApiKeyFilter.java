package com.accesmed.backend.Security.Agente;

import com.accesmed.backend.Controllers.Errors.AccesMedError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Filtro de autenticación del canal chatbot: exige el header {@code X-Agente-Api-Key} con la
 * clave compartida con Flowise ({@code accesmed.agente.api-key}). Si coincide, deja autenticado
 * al principal {@code agente-whatsapp}; si no, responde 401 con el contrato {@code AccesMedError}.
 * No es un bean: lo instancia {@code SecurityFilterChainConfig} solo dentro de la cadena
 * {@code /accesmed-api/Agente/**}, para que Spring Boot no lo registre además como filtro global.
 */
@Slf4j
public class AgenteApiKeyFilter extends OncePerRequestFilter {

    //region ========== Constantes ==========

    /**
     * Nombre del header que transporta la clave del agente.
     */
    public static final String HEADER_API_KEY = "X-Agente-Api-Key";

    /**
     * Principal con el que queda autenticado el agente.
     */
    public static final String PRINCIPAL_AGENTE = "agente-whatsapp";

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final byte[] huellaApiKeyEsperada;
    private final ObjectMapper objectMapper;

    //endregion

    /**
     * Construye el filtro con la clave esperada.
     *
     * @param apiKeyEsperada {@code String} clave que debe presentar el agente
     * @param objectMapper {@code ObjectMapper} serializador del {@code AccesMedError}
     */
    public AgenteApiKeyFilter(String apiKeyEsperada, ObjectMapper objectMapper) {

        this.huellaApiKeyEsperada = huella(apiKeyEsperada);
        this.objectMapper = objectMapper;

    }

    //region ========== Métodos ==========

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKeyRecibida = request.getHeader(HEADER_API_KEY);

        //Se compara la huella SHA-256 de ambas claves en tiempo constante: no filtra ni el contenido ni el largo
        if (apiKeyRecibida == null || !MessageDigest.isEqual(huella(apiKeyRecibida), huellaApiKeyEsperada)) {
            log.warn("Request al agente rechazado en {}: falta la API key o es incorrecta", request.getRequestURI());
            writeNoAutenticado(request, response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                PRINCIPAL_AGENTE, null, List.of(new SimpleGrantedAuthority("ROLE_AGENTE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);

    }

    /**
     * Escribe el {@code AccesMedError} de "no autenticado" con status 401.
     *
     * @param request {@code HttpServletRequest} request rechazado
     * @param response {@code HttpServletResponse} response a completar
     * @throws IOException {@code IOException} si falla la escritura de la respuesta
     */
    private void writeNoAutenticado(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String mensaje = "No se pudo autenticar al agente: falta la clave de acceso o es inválida.";
        AccesMedError accesMedError = AccesMedError.of(HttpStatus.UNAUTHORIZED.value(), "AGENTE_NO_AUTENTICADO",
                mensaje, List.of(mensaje), request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), accesMedError);

    }

    /**
     * Calcula la huella SHA-256 de una clave.
     *
     * @param clave {@code String} clave a resumir
     * @return {@code byte[]} huella SHA-256 de la clave
     */
    private static byte[] huella(String clave) {

        try {
            return MessageDigest.getInstance("SHA-256").digest(clave.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException excepcion) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", excepcion);
        }

    }

    //endregion

}
