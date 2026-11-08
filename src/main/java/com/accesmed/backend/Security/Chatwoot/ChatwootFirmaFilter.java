package com.accesmed.backend.Security.Chatwoot;

import com.accesmed.backend.Controllers.Errors.AccesMedError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;

/**
 * Filtro de autenticidad del webhook del Agent Bot de Chatwoot. Verifica la firma
 * {@code X-Chatwoot-Signature} = {@code sha256=} + hex(HMAC-SHA256(secret, "{timestamp}.{body crudo}")),
 * comparada en tiempo constante, con un {@code X-Chatwoot-Timestamp} de hasta 5 minutos. Si falla
 * responde 401 con el contrato {@code AccesMedError}; si pasa, re-expone el body leído para que el
 * controller lo reciba intacto. Con el secret vacío no verifica (solo para pruebas) y deja un
 * {@code warn}. No es un bean: lo instancia {@code SecurityFilterChainConfig} dentro de la cadena
 * {@code /accesmed-api/Chatwoot/**}.
 */
@Slf4j
public class ChatwootFirmaFilter extends OncePerRequestFilter {

    //region ========== Constantes ==========

    /**
     * Header con la firma HMAC del request.
     */
    public static final String HEADER_FIRMA = "X-Chatwoot-Signature";

    /**
     * Header con el momento de la firma (epoch en segundos).
     */
    public static final String HEADER_TIMESTAMP = "X-Chatwoot-Timestamp";

    private static final long TOLERANCIA_TIMESTAMP_SEGUNDOS = 300;
    private static final String PREFIJO_FIRMA = "sha256=";

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    //endregion

    /**
     * Construye el filtro con el secret del Agent Bot.
     *
     * @param webhookSecret {@code String} secret del Agent Bot ({@code accesmed.chatwoot.webhook-secret}); vacío no verifica
     * @param objectMapper {@code ObjectMapper} serializador del {@code AccesMedError}
     * @param clock {@code Clock} reloj para validar la antigüedad del timestamp
     */
    public ChatwootFirmaFilter(String webhookSecret, ObjectMapper objectMapper, Clock clock) {

        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
        this.clock = clock;

    }

    //region ========== Métodos ==========

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        byte[] cuerpoCrudo = request.getInputStream().readAllBytes();

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("accesmed.chatwoot.webhook-secret está vacío: el webhook de Chatwoot se acepta SIN verificar la firma");
        } else if (!isFirmaValida(cuerpoCrudo, request.getHeader(HEADER_FIRMA), request.getHeader(HEADER_TIMESTAMP))) {
            writeNoAutenticado(request, response);
            return;
        }

        filterChain.doFilter(new RequestConCuerpo(request, cuerpoCrudo), response);

    }

    //endregion

    //region ========== Helpers privados ==========

    /**
     * Indica si la firma del request es válida y su timestamp está dentro de la ventana.
     *
     * @param cuerpoCrudo {@code byte[]} body del request tal cual llegó
     * @param firma {@code String} header de firma, o {@code null}
     * @param timestamp {@code String} header de timestamp, o {@code null}
     * @return {@code boolean} {@code true} si el request viene de Chatwoot
     */
    private boolean isFirmaValida(byte[] cuerpoCrudo, String firma, String timestamp) {

        if (firma == null || timestamp == null) {
            log.warn("Webhook de Chatwoot rechazado: faltan los headers de firma");
            return false;
        }

        long timestampSegundos;
        try {
            timestampSegundos = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException excepcion) {
            log.warn("Webhook de Chatwoot rechazado: timestamp no numérico");
            return false;
        }

        if (Math.abs(clock.instant().getEpochSecond() - timestampSegundos) > TOLERANCIA_TIMESTAMP_SEGUNDOS) {
            log.warn("Webhook de Chatwoot rechazado: timestamp fuera de la ventana de {} s", TOLERANCIA_TIMESTAMP_SEGUNDOS);
            return false;
        }

        String firmaEsperada = PREFIJO_FIRMA + calculateHmacHex(timestamp.trim(), cuerpoCrudo);
        boolean firmaValida = MessageDigest.isEqual(
                firmaEsperada.getBytes(StandardCharsets.UTF_8), firma.trim().getBytes(StandardCharsets.UTF_8));
        if (!firmaValida) {
            log.warn("Webhook de Chatwoot rechazado: la firma no coincide");
        }
        return firmaValida;

    }

    /**
     * Calcula HMAC-SHA256 de {@code "<timestamp>.<cuerpo>"} con el secret.
     *
     * @param timestamp {@code String} timestamp del header
     * @param cuerpoCrudo {@code byte[]} body del request
     * @return {@code String} la firma en hexadecimal minúscula
     */
    private String calculateHmacHex(String timestamp, byte[] cuerpoCrudo) {

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(cuerpoCrudo));
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException("HmacSHA256 no está disponible en esta JVM", excepcion);
        }

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

        String mensaje = "No se pudo verificar que el webhook venga de Chatwoot: la firma falta, es inválida o está vencida.";
        AccesMedError accesMedError = AccesMedError.of(HttpStatus.UNAUTHORIZED.value(), "CHATWOOT_NO_AUTENTICADO",
                mensaje, List.of(mensaje), request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), accesMedError);

    }

    /**
     * Request que vuelve a exponer el body ya leído por el filtro.
     */
    private static final class RequestConCuerpo extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        private RequestConCuerpo(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream entrada = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return entrada.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no soportada");
                }

                @Override
                public int read() {
                    return entrada.read();
                }

                @Override
                public int read(byte[] destino, int desde, int largo) {
                    return entrada.read(destino, desde, largo);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }

    }

    //endregion

}
