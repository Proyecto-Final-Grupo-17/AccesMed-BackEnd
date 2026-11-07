package com.accesmed.backend.Application;

import com.accesmed.backend.Services.Clients.ChatwootClient;
import com.accesmed.backend.Services.Clients.FlowiseClient;
import com.accesmed.backend.Services.Errors.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link ChatwootApp} con los clientes mockeados y un executor síncrono:
 * firma HMAC (válida, inválida, vieja), filtrado de eventos, handoff y fallo de Flowise.
 */
@ExtendWith(MockitoExtension.class)
class ChatwootAppTest {

    private static final String SECRET = "secret-de-prueba";
    private static final String TELEFONO = "+5492617742682";

    @Mock
    private ChatwootClient chatwootClient;
    @Mock
    private FlowiseClient flowiseClient;

    private ChatwootApp chatwootApp;

    @BeforeEach
    void setUp() {
        chatwootApp = new ChatwootApp(JsonMapper.builder().build(), chatwootClient, flowiseClient, Runnable::run, SECRET);
    }

    //region ========== firma ==========

    @Test
    void receive_firmaValida_procesaYResponde() {
        String cuerpo = payload("message_created", "incoming", "pending", false, "Hola", TELEFONO);
        String timestamp = ahora();
        when(flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-77", TELEFONO)).thenReturn("¡Hola! ¿En qué te ayudo?");

        chatwootApp.receiveMensajeChatwoot(cuerpo, firmar(timestamp, cuerpo), timestamp);

        verify(chatwootClient).sendMensajeChatwoot(77, "¡Hola! ¿En qué te ayudo?");
        verify(chatwootClient, never()).openConversacionChatwoot(anyLong());
    }

    @Test
    void receive_firmaInvalida_lanzaReglaNegocioYNoLlamaANadie() {
        String cuerpo = payload("message_created", "incoming", "pending", false, "Hola", TELEFONO);

        assertThrows(ReglaNegocioException.class,
                () -> chatwootApp.receiveMensajeChatwoot(cuerpo, "sha256=deadbeef", ahora()));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_cuerpoAlteradoDespuesDeFirmar_lanzaReglaNegocio() {
        String cuerpo = payload("message_created", "incoming", "pending", false, "Hola", TELEFONO);
        String timestamp = ahora();
        String firma = firmar(timestamp, cuerpo);

        assertThrows(ReglaNegocioException.class,
                () -> chatwootApp.receiveMensajeChatwoot(cuerpo + " ", firma, timestamp));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_timestampVencido_lanzaReglaNegocioAunqueLaFirmaSeaCorrecta() {
        String cuerpo = payload("message_created", "incoming", "pending", false, "Hola", TELEFONO);
        String timestampViejo = String.valueOf(Instant.now().getEpochSecond() - 600);

        assertThrows(ReglaNegocioException.class,
                () -> chatwootApp.receiveMensajeChatwoot(cuerpo, firmar(timestampViejo, cuerpo), timestampViejo));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_sinHeadersDeFirmaYConSecret_lanzaReglaNegocio() {
        String cuerpo = payload("message_created", "incoming", "pending", false, "Hola", TELEFONO);

        assertThrows(ReglaNegocioException.class, () -> chatwootApp.receiveMensajeChatwoot(cuerpo, null, null));
    }

    @Test
    void receive_sinSecretConfigurado_aceptaSinVerificarFirma() {
        ChatwootApp appSinSecret = new ChatwootApp(JsonMapper.builder().build(), chatwootClient, flowiseClient, Runnable::run, "");
        String cuerpo = payload("message_created", "incoming", "pending", false, "Hola", TELEFONO);
        when(flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-77", TELEFONO)).thenReturn("Hola");

        appSinSecret.receiveMensajeChatwoot(cuerpo, null, null);

        verify(chatwootClient).sendMensajeChatwoot(77, "Hola");
    }

    @Test
    void receive_jsonInvalido_lanzaValidacion() {
        String cuerpo = "esto no es json";
        String timestamp = ahora();

        assertThrows(ValidacionException.class,
                () -> chatwootApp.receiveMensajeChatwoot(cuerpo, firmar(timestamp, cuerpo), timestamp));
    }

    //endregion

    //region ========== filtrado ==========

    @Test
    void receive_mensajeSaliente_seIgnora() {
        recibir(payload("message_created", "outgoing", "pending", false, "Respuesta del bot", TELEFONO));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_conversacionAbierta_seIgnoraPorqueLaTomoUnaPersona() {
        recibir(payload("message_created", "incoming", "open", false, "Hola", TELEFONO));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_notaPrivada_seIgnora() {
        recibir(payload("message_created", "incoming", "pending", true, "Nota interna", TELEFONO));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_otroEvento_seIgnora() {
        recibir(payload("conversation_status_changed", "incoming", "pending", false, "Hola", TELEFONO));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_mensajeSinTexto_seIgnora() {
        recibir(payload("message_created", "incoming", "pending", false, "", TELEFONO));

        verifyNoInteractions(flowiseClient, chatwootClient);
    }

    @Test
    void receive_camposDesconocidosEnElPayload_seIgnoran() {
        String cuerpo = "{\"event\":\"message_created\",\"message_type\":\"incoming\",\"private\":false,"
                + "\"content\":\"Hola\",\"campo_nuevo\":{\"x\":1},"
                + "\"conversation\":{\"id\":5,\"status\":\"pending\",\"otro\":1},"
                + "\"sender\":{\"phone_number\":\"" + TELEFONO + "\",\"extra\":true}}";
        when(flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-5", TELEFONO)).thenReturn("Hola");

        recibir(cuerpo);

        verify(chatwootClient).sendMensajeChatwoot(5, "Hola");
    }

    @Test
    void receive_telefonoSoloEnElContactoDeLaConversacion_usaElDeRespaldo() {
        String cuerpo = "{\"event\":\"message_created\",\"message_type\":\"incoming\",\"private\":false,"
                + "\"content\":\"Hola\",\"sender\":{\"name\":\"Ana\"},"
                + "\"conversation\":{\"id\":9,\"status\":\"pending\","
                + "\"meta\":{\"sender\":{\"phone_number\":\"" + TELEFONO + "\"}}}}";
        when(flowiseClient.sendPreguntaFlowise("Hola", "chatwoot-9", TELEFONO)).thenReturn("Hola");

        recibir(cuerpo);

        verify(flowiseClient).sendPreguntaFlowise("Hola", "chatwoot-9", TELEFONO);
    }

    @Test
    void receive_sinTelefono_derivaAUnaPersonaSinLlamarAFlowise() {
        String cuerpo = "{\"event\":\"message_created\",\"message_type\":\"incoming\",\"private\":false,"
                + "\"content\":\"Hola\",\"conversation\":{\"id\":9,\"status\":\"pending\"}}";

        recibir(cuerpo);

        verifyNoInteractions(flowiseClient);
        verify(chatwootClient).sendMensajeChatwoot(9, ChatwootApp.MENSAJE_ERROR_TECNICO);
        verify(chatwootClient).openConversacionChatwoot(9);
    }

    //endregion

    //region ========== handoff y errores ==========

    @Test
    void receive_respuestaConHandoff_quitaElTokenEnviaElTextoYAbreLaConversacion() {
        when(flowiseClient.sendPreguntaFlowise(anyString(), eq("chatwoot-77"), eq(TELEFONO)))
                .thenReturn("Te comunico con una persona. " + ChatwootApp.TOKEN_HANDOFF);

        recibir(payload("message_created", "incoming", "pending", false, "Quiero hablar con alguien", TELEFONO));

        var orden = inOrder(chatwootClient);
        orden.verify(chatwootClient).sendMensajeChatwoot(77, "Te comunico con una persona.");
        orden.verify(chatwootClient).openConversacionChatwoot(77);
    }

    @Test
    void receive_respuestaSoloConHandoff_noEnviaMensajeVacioPeroAbreLaConversacion() {
        when(flowiseClient.sendPreguntaFlowise(anyString(), anyString(), anyString())).thenReturn(ChatwootApp.TOKEN_HANDOFF);

        recibir(payload("message_created", "incoming", "pending", false, "Operador", TELEFONO));

        verify(chatwootClient, never()).sendMensajeChatwoot(anyLong(), anyString());
        verify(chatwootClient).openConversacionChatwoot(77);
    }

    @Test
    void receive_flowiseFalla_avisaAlPacienteYDerivaAUnaPersona() {
        when(flowiseClient.sendPreguntaFlowise(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Flowise caído"));

        recibir(payload("message_created", "incoming", "pending", false, "Hola", TELEFONO));

        var orden = inOrder(chatwootClient);
        orden.verify(chatwootClient).sendMensajeChatwoot(77, ChatwootApp.MENSAJE_ERROR_TECNICO);
        orden.verify(chatwootClient).openConversacionChatwoot(77);
    }

    @Test
    void receive_flowiseDevuelveVacio_tambienDerivaAUnaPersona() {
        when(flowiseClient.sendPreguntaFlowise(anyString(), anyString(), anyString())).thenReturn(null);

        recibir(payload("message_created", "incoming", "pending", false, "Hola", TELEFONO));

        verify(chatwootClient).sendMensajeChatwoot(77, ChatwootApp.MENSAJE_ERROR_TECNICO);
        verify(chatwootClient).openConversacionChatwoot(77);
    }

    @Test
    void receive_chatwootFallaAlResponder_noPropagaLaExcepcion() {
        when(flowiseClient.sendPreguntaFlowise(anyString(), anyString(), anyString())).thenReturn("Hola");
        org.mockito.Mockito.doThrow(new IllegalStateException("Chatwoot caído"))
                .when(chatwootClient).sendMensajeChatwoot(anyLong(), anyString());

        recibir(payload("message_created", "incoming", "pending", false, "Hola", TELEFONO));

        verify(chatwootClient, never()).openConversacionChatwoot(anyLong());
    }

    //endregion

    //region ========== helpers ==========

    private void recibir(String cuerpo) {
        String timestamp = ahora();
        chatwootApp.receiveMensajeChatwoot(cuerpo, firmar(timestamp, cuerpo), timestamp);
    }

    private String ahora() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private String firmar(String timestamp, String cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException excepcion) {
            throw new IllegalStateException(excepcion);
        }
    }

    private String payload(String evento, String tipoMensaje, String estadoConversacion, boolean privado,
            String contenido, String telefono) {
        return "{\"event\":\"" + evento + "\",\"message_type\":\"" + tipoMensaje + "\",\"private\":" + privado
                + ",\"content\":\"" + contenido + "\","
                + "\"conversation\":{\"id\":77,\"status\":\"" + estadoConversacion + "\"},"
                + "\"sender\":{\"id\":1,\"name\":\"Ana\",\"phone_number\":\"" + telefono + "\"}}";
    }

    //endregion

}
