package com.accesmed.backend.Services.Clients;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link FlowiseClient} y {@link ChatwootClient} contra un servidor HTTP local: verifican
 * la ruta, los headers y el body que arma cada cliente y cómo interpreta la respuesta.
 */
class ClientesHttpTest {

    private HttpServer servidor;
    private String baseUrl;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> apiAccessToken = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = "{}";

    @BeforeEach
    void setUp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            apiAccessToken.set(intercambio.getRequestHeaders().getFirst("api_access_token"));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(status, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    @Test
    void flowise_armaLaPrediccionConSesionYVariableDeTelefonoYDevuelveElTexto() {
        respuesta = "{\"text\":\"Hola, soy AccesMed\",\"otro\":1}";
        FlowiseClient flowiseClient = new FlowiseClient(baseUrl, "chatflow-123", "clave-flowise");

        String texto = flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-77", "+5492617742682");

        assertEquals("Hola, soy AccesMed", texto);
        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/prediction/chatflow-123", ruta.get());
        assertEquals("Bearer clave-flowise", authorization.get());
        assertTrue(cuerpo.get().contains("\"question\":\"Hola\""));
        assertTrue(cuerpo.get().contains("\"sessionId\":\"chatwoot-77\""));
        assertTrue(cuerpo.get().contains("\"numeroTelefono\":\"+5492617742682\""));
    }

    @Test
    void flowise_sinApiKey_noEnviaElHeaderAuthorization() {
        respuesta = "{\"text\":\"ok\"}";
        FlowiseClient flowiseClient = new FlowiseClient(baseUrl, "chatflow-123", "");

        flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-77", "+5492617742682");

        assertNull(authorization.get());
    }

    @Test
    void flowise_respuestaSinTexto_devuelveNulo() {
        respuesta = "{\"json\":{}}";
        FlowiseClient flowiseClient = new FlowiseClient(baseUrl, "chatflow-123", "");

        assertNull(flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-77", "+5492617742682"));
    }

    @Test
    void flowise_errorHttp_lanzaExcepcion() {
        status = 500;
        FlowiseClient flowiseClient = new FlowiseClient(baseUrl, "chatflow-123", "");

        assertThrows(RuntimeException.class, () -> flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-77", "+5492617742682"));
    }

    @Test
    void chatwoot_enviaMensajeSalienteConElTokenDeAcceso() {
        ChatwootClient chatwootClient = new ChatwootClient(baseUrl, "1", "token-chatwoot");

        chatwootClient.sendMensajeChatwoot(77, "Hola");

        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/accounts/1/conversations/77/messages", ruta.get());
        assertEquals("token-chatwoot", apiAccessToken.get());
        assertTrue(cuerpo.get().contains("\"content\":\"Hola\""));
        assertTrue(cuerpo.get().contains("\"message_type\":\"outgoing\""));
        assertTrue(cuerpo.get().contains("\"private\":false"));
    }

    @Test
    void chatwoot_abreLaConversacion() {
        ChatwootClient chatwootClient = new ChatwootClient(baseUrl, "1", "token-chatwoot");

        chatwootClient.openConversacionChatwoot(77);

        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/accounts/1/conversations/77/toggle_status", ruta.get());
        assertTrue(cuerpo.get().contains("\"status\":\"open\""));
    }

    @Test
    void chatwoot_errorHttp_lanzaExcepcion() {
        status = 401;
        ChatwootClient chatwootClient = new ChatwootClient(baseUrl, "1", "token-malo");

        assertThrows(RuntimeException.class, () -> chatwootClient.sendMensajeChatwoot(77, "Hola"));
    }

}
