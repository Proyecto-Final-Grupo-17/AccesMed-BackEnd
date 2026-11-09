package com.accesmed.backend.Services.Clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP de OpenAI para el audio del canal ({@code Docs/Diseno-Chatbot.md §5}): transcribe los
 * audios del paciente y sintetiza la voz de la respuesta. Usa {@code RestClient} con timeouts
 * explícitos y autentica con el header {@code Authorization: Bearer}. Con la API key vacía el cliente
 * igual se construye (el audio queda apagado desde {@code OpenAiService}, que nunca lo invoca).
 */
@Slf4j
@Component
public class OpenAiClient {

    //region ========== Constantes ==========

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(5);

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final RestClient restClient;
    private final String modeloTranscripcion;
    private final String modeloVoz;
    private final String voz;
    private final String instruccionesVoz;

    //endregion

    /**
     * Construye el cliente de OpenAI.
     *
     * @param baseUrl {@code String} URL base de OpenAI ({@code accesmed.openai.base-url})
     * @param apiKey {@code String} API key ({@code accesmed.openai.api-key}); vacía = audio apagado
     * @param modeloTranscripcion {@code String} modelo de transcripción ({@code accesmed.audio.modelo-transcripcion})
     * @param modeloVoz {@code String} modelo de voz ({@code accesmed.audio.modelo-voz})
     * @param voz {@code String} voz de la síntesis ({@code accesmed.audio.voz})
     * @param instruccionesVoz {@code String} instrucciones de tono y ritmo de la voz ({@code accesmed.audio.instrucciones-voz})
     * @param timeoutSegundos {@code long} segundos máximos esperando a OpenAI ({@code accesmed.audio.timeout-segundos})
     */
    public OpenAiClient(@Value("${accesmed.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${accesmed.openai.api-key:}") String apiKey,
            @Value("${accesmed.audio.modelo-transcripcion:gpt-4o-mini-transcribe}") String modeloTranscripcion,
            @Value("${accesmed.audio.modelo-voz:gpt-4o-mini-tts}") String modeloVoz,
            @Value("${accesmed.audio.voz:coral}") String voz,
            @Value("${accesmed.audio.instrucciones-voz:}") String instruccionesVoz,
            @Value("${accesmed.audio.timeout-segundos:30}") long timeoutSegundos) {

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));

        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
        if (apiKey != null && !apiKey.isBlank()) {
            restClientBuilder.defaultHeaders(headers -> headers.setBearerAuth(apiKey));
        }
        this.restClient = restClientBuilder.build();
        this.modeloTranscripcion = modeloTranscripcion;
        this.modeloVoz = modeloVoz;
        this.voz = voz;
        this.instruccionesVoz = instruccionesVoz;

    }

    //region ========== Métodos ==========

    /**
     * Transcribe un audio a texto, en español.
     *
     * @param audio {@code byte[]} contenido del audio
     * @param nombreArchivo {@code String} nombre del archivo (OpenAI deduce el formato de la extensión)
     * @param mime {@code String} tipo MIME del audio (ej. {@code audio/ogg})
     * @return {@code String} el texto transcripto, o {@code null} si OpenAI no devolvió texto
     * @throws org.springframework.web.client.RestClientException si OpenAI no responde o devuelve error
     */
    @SuppressWarnings("unchecked")
    public String transcribeAudio(byte[] audio, String nombreArchivo, String mime) {

        log.debug("Transcribiendo audio con OpenAI: bytes={}, modelo={}", audio.length, modeloTranscripcion);

        ByteArrayResource archivoAudio = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        };
        //MultipartBodyBuilder no sirve acá: pide reactive-streams, que el back no trae (sin WebFlux)
        HttpHeaders cabecerasArchivo = new HttpHeaders();
        cabecerasArchivo.setContentType(MediaType.parseMediaType(mime));
        MultiValueMap<String, Object> cuerpoMultipart = new LinkedMultiValueMap<>();
        cuerpoMultipart.add("file", new HttpEntity<>(archivoAudio, cabecerasArchivo));
        cuerpoMultipart.add("model", modeloTranscripcion);
        cuerpoMultipart.add("language", "es");
        cuerpoMultipart.add("response_format", "json");

        Map<String, Object> respuestaTranscripcion = restClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpoMultipart)
                .retrieve()
                .body(Map.class);

        String textoTranscripto = respuestaTranscripcion != null && respuestaTranscripcion.get("text") instanceof String texto
                ? texto : null;
        return textoTranscripto;

    }

    /**
     * Sintetiza un texto en una nota de voz.
     *
     * @param texto {@code String} texto a leer (hasta 4096 caracteres, tope de la API)
     * @return {@code byte[]} el audio en formato OGG/Opus
     * @throws org.springframework.web.client.RestClientException si OpenAI no responde o devuelve error
     */
    public byte[] createVoz(String texto) {

        log.debug("Sintetizando voz con OpenAI: caracteres={}, modelo={}, voz={}", texto.length(), modeloVoz, voz);

        Map<String, Object> cuerpoVoz = Map.of(
                "model", modeloVoz,
                "voice", voz,
                "input", texto,
                "instructions", instruccionesVoz,
                "response_format", "opus");

        byte[] audioVoz = restClient.post()
                .uri("/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpoVoz)
                .retrieve()
                .body(byte[].class);
        return audioVoz;

    }

    //endregion

}
