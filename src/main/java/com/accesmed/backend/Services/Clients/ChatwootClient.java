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

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de la API de Chatwoot: responde mensajes (texto y notas de voz) del bot, deja notas
 * privadas, descarga los adjuntos del paciente y deriva la conversación a una persona. Autentica con
 * el header {@code api_access_token}.
 */
@Slf4j
@Component
public class ChatwootClient {

    //region ========== Constantes ==========

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(15);

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final RestClient restClient;
    private final String accountId;

    //endregion

    /**
     * Construye el cliente de Chatwoot.
     *
     * @param baseUrl {@code String} URL base de Chatwoot ({@code accesmed.chatwoot.base-url})
     * @param accountId {@code String} id de la cuenta ({@code accesmed.chatwoot.account-id})
     * @param apiToken {@code String} token de acceso ({@code accesmed.chatwoot.api-token})
     */
    public ChatwootClient(@Value("${accesmed.chatwoot.base-url}") String baseUrl,
            @Value("${accesmed.chatwoot.account-id}") String accountId,
            @Value("${accesmed.chatwoot.api-token}") String apiToken) {

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT_CONEXION)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
        requestFactory.setReadTimeout(TIMEOUT_LECTURA);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("api_access_token", apiToken)
                .build();
        this.accountId = accountId;

    }

    //region ========== Métodos ==========

    /**
     * Envía un mensaje saliente (visible para el paciente) a una conversación.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param contenido {@code String} texto del mensaje
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public void sendMensajeChatwoot(long conversationId, String contenido) {

        log.debug("Enviando mensaje a la conversación de Chatwoot: id={}", conversationId);

        restClient.post()
                .uri("/api/v1/accounts/{accountId}/conversations/{conversationId}/messages", accountId, conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", contenido, "message_type", "outgoing", "private", false))
                .retrieve()
                .toBodilessEntity();

    }

    /**
     * Envía una nota privada (solo la ve el equipo) a una conversación.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param texto {@code String} texto de la nota
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public void sendNotaPrivadaChatwoot(long conversationId, String texto) {

        log.debug("Enviando nota privada a la conversación de Chatwoot: id={}", conversationId);

        restClient.post()
                .uri("/api/v1/accounts/{accountId}/conversations/{conversationId}/messages", accountId, conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", texto, "message_type", "outgoing", "private", true))
                .retrieve()
                .toBodilessEntity();

    }

    /**
     * Envía un audio saliente (nota de voz, visible para el paciente) a una conversación. Va sin
     * {@code content}: WhatsApp no manda pie de texto en los audios.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param audio {@code byte[]} audio en formato OGG/Opus
     * @param nombreArchivo {@code String} nombre del archivo adjunto (ej. {@code respuesta.ogg})
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public void sendAudioChatwoot(long conversationId, byte[] audio, String nombreArchivo) {

        log.debug("Enviando audio a la conversación de Chatwoot: id={}, bytes={}", conversationId, audio.length);

        ByteArrayResource archivoAudio = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        };
        //MultipartBodyBuilder no sirve acá: pide reactive-streams, que el back no trae (sin WebFlux)
        HttpHeaders cabecerasArchivo = new HttpHeaders();
        cabecerasArchivo.setContentType(MediaType.parseMediaType("audio/ogg"));
        MultiValueMap<String, Object> cuerpoMultipart = new LinkedMultiValueMap<>();
        cuerpoMultipart.add("attachments[]", new HttpEntity<>(archivoAudio, cabecerasArchivo));
        cuerpoMultipart.add("message_type", "outgoing");
        cuerpoMultipart.add("private", "false");

        restClient.post()
                .uri("/api/v1/accounts/{accountId}/conversations/{conversationId}/messages", accountId, conversationId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpoMultipart)
                .retrieve()
                .toBodilessEntity();

    }

    /**
     * Descarga el archivo de un adjunto del paciente. La URL de Chatwoot (ActiveStorage) redirige al
     * archivo, por eso el cliente sigue redirecciones; también manda el {@code api_access_token}, por
     * si la URL lo pide.
     *
     * @param dataUrl {@code String} URL absoluta del adjunto ({@code data_url} del webhook)
     * @return {@code byte[]} el contenido del archivo
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public byte[] downloadAdjuntoChatwoot(String dataUrl) {

        log.debug("Descargando adjunto de Chatwoot");

        byte[] contenidoAdjunto = restClient.get()
                .uri(URI.create(dataUrl))
                .retrieve()
                .body(byte[].class);
        return contenidoAdjunto;

    }

    /**
     * Pasa la conversación a {@code open}: deja de ser del bot y cae en la bandeja de las personas.
     *
     * @param conversationId {@code long} identificador de la conversación
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public void openConversacionChatwoot(long conversationId) {

        log.debug("Derivando la conversación de Chatwoot a una persona: id={}", conversationId);

        restClient.post()
                .uri("/api/v1/accounts/{accountId}/conversations/{conversationId}/toggle_status", accountId, conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "open"))
                .retrieve()
                .toBodilessEntity();

    }

    /**
     * Pasa la conversación a {@code resolved}: la charla terminó. Si el paciente vuelve a escribir,
     * Chatwoot abre una conversación nueva en {@code pending} (con el bot).
     *
     * @param conversationId {@code long} identificador de la conversación
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public void resolveConversacionChatwoot(long conversationId) {

        log.debug("Resolviendo la conversación de Chatwoot: id={}", conversationId);

        restClient.post()
                .uri("/api/v1/accounts/{accountId}/conversations/{conversationId}/toggle_status", accountId, conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "resolved"))
                .retrieve()
                .toBodilessEntity();

    }

    /**
     * Agrega etiquetas a la conversación (la etiqueta tiene que existir en Chatwoot).
     *
     * @param conversationId {@code long} identificador de la conversación
     * @param etiquetas {@code List<String>} etiquetas a agregar
     * @throws org.springframework.web.client.RestClientException si Chatwoot no responde o devuelve error
     */
    public void addEtiquetasConversacionChatwoot(long conversationId, List<String> etiquetas) {

        log.debug("Etiquetando la conversación de Chatwoot: id={}, etiquetas={}", conversationId, etiquetas);

        restClient.post()
                .uri("/api/v1/accounts/{accountId}/conversations/{conversationId}/labels", accountId, conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("labels", etiquetas))
                .retrieve()
                .toBodilessEntity();

    }

    //endregion

}
