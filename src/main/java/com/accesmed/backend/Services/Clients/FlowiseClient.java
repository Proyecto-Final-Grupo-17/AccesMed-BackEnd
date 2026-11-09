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
 * Cliente HTTP de Flowise: le manda la pregunta del paciente al chatflow del agente y devuelve
 * su respuesta. Usa {@code RestClient} con timeouts explícitos (el LLM puede tardar).
 */
@Slf4j
@Component
public class FlowiseClient {

    //region ========== Dependencias o inyecciones ==========

    private final RestClient restClient;
    private final String chatflowId;
    private final String apiKey;

    //endregion

    /**
     * Construye el cliente de Flowise.
     *
     * @param baseUrl {@code String} URL base de Flowise ({@code accesmed.flowise.base-url})
     * @param chatflowId {@code String} id del chatflow del agente ({@code accesmed.flowise.chatflow-id})
     * @param apiKey {@code String} API key del chatflow ({@code accesmed.flowise.api-key}); vacía si no se usa
     * @param timeoutConexionSegundos {@code long} segundos máximos para conectar ({@code accesmed.flowise.timeout-conexion-segundos})
     * @param timeoutLecturaSegundos {@code long} segundos máximos esperando la respuesta del agente ({@code accesmed.flowise.timeout-lectura-segundos});
     *        al vencer, el canal deriva la conversación a una persona
     */
    public FlowiseClient(@Value("${accesmed.flowise.base-url}") String baseUrl,
            @Value("${accesmed.flowise.chatflow-id}") String chatflowId,
            @Value("${accesmed.flowise.api-key:}") String apiKey,
            @Value("${accesmed.flowise.timeout-conexion-segundos:5}") long timeoutConexionSegundos,
            @Value("${accesmed.flowise.timeout-lectura-segundos:60}") long timeoutLecturaSegundos) {

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutConexionSegundos)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutLecturaSegundos));

        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.chatflowId = chatflowId;
        this.apiKey = apiKey;

    }

    //region ========== Métodos ==========

    /**
     * Envía la pregunta del paciente al chatflow de Flowise y devuelve el texto de la respuesta.
     * La sesión mantiene la memoria de la conversación; los datos del canal viajan como variables
     * ({@code $vars.<nombre>}, contrato en {@code Docs/Features/Chatbot.md §6.D}): el teléfono lo
     * reenvían los Custom Tools al back en el header {@code X-Paciente-Telefono}.
     *
     * @param pregunta {@code String} mensaje del paciente (una tanda: mensajes unidos por salto de línea)
     * @param sessionId {@code String} identificador de sesión de la conversación
     * @param variablesFlowise {@code Map<String, String>} variables de sesión ({@code numeroTelefono}, {@code nombreContacto}, etc.)
     * @return {@code String} el texto que respondió el agente, o {@code null} si Flowise no devolvió texto
     * @throws org.springframework.web.client.RestClientException si Flowise no responde o devuelve error
     */
    @SuppressWarnings("unchecked")
    public String sendPreguntaFlowise(String pregunta, String sessionId, Map<String, String> variablesFlowise) {

        log.debug("Enviando pregunta a Flowise: sessionId={}", sessionId);

        Map<String, Object> body = Map.of(
                "question", pregunta,
                "overrideConfig", Map.of(
                        "sessionId", sessionId,
                        "vars", variablesFlowise));

        RestClient.RequestBodySpec request = restClient.post()
                .uri("/api/v1/prediction/{chatflowId}", chatflowId)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            request = request.headers(headers -> headers.setBearerAuth(apiKey));
        }

        Map<String, Object> respuesta = request.body(body).retrieve().body(Map.class);

        return respuesta != null && respuesta.get("text") instanceof String texto ? texto : null;

    }

    //endregion

}
