package com.accesmed.backend.Services.Clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP de la API de Chatwoot: responde mensajes del bot y deriva la conversación a una
 * persona. Autentica con el header {@code api_access_token}.
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
                HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build());
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

    //endregion

}
