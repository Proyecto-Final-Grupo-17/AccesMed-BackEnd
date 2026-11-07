package com.accesmed.backend.Security.Config;

import com.accesmed.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración de las cadenas del canal chatbot ({@code agenteSecurityFilterChain} y
 * {@code chatwootSecurityFilterChain} en {@link SecurityFilterChainConfig}): {@code /Agente/**}
 * exige la API key de {@code accesmed.agente.api-key} (ver {@code application-test.yml}), el
 * webhook de Chatwoot no pide JWT y el panel sigue pidiendo JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AgenteSecurityTest {

    private static final String HEADER_API_KEY = "X-Agente-Api-Key";
    private static final String API_KEY = "test-agente-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void agente_sinApiKey_401ConFormatoAccesMedError() throws Exception {
        mockMvc.perform(get("/accesmed-api/Agente/Clinica"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("AGENTE_NO_AUTENTICADO"))
                .andExpect(jsonPath("$.path").value("/accesmed-api/Agente/Clinica"));
    }

    @Test
    void agente_apiKeyIncorrecta_401() throws Exception {
        mockMvc.perform(get("/accesmed-api/Agente/Clinica").header(HEADER_API_KEY, "incorrecta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agente_apiKeyCorrecta_pasaLaCadenaYLlegaAlController() throws Exception {
        mockMvc.perform(get("/accesmed-api/Agente/Clinica").header(HEADER_API_KEY, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").exists());
    }

    @Test
    void agente_apiKeyCorrectaSinTelefono_422PorqueNoSePuedeIdentificarAlPaciente() throws Exception {
        mockMvc.perform(get("/accesmed-api/Agente/Paciente").header(HEADER_API_KEY, API_KEY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void agente_apiKeyCorrectaConTelefonoDesconocido_404PacienteNoEncontrado() throws Exception {
        mockMvc.perform(get("/accesmed-api/Agente/Paciente")
                        .header(HEADER_API_KEY, API_KEY)
                        .header("X-Paciente-Telefono", "+5491100000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PACIENTE_NO_ENCONTRADO"));
    }

    @Test
    void panel_conLaApiKeyDelAgente_sigueExigiendoJwt() throws Exception {
        mockMvc.perform(get("/accesmed-api/Turno/Turno").header(HEADER_API_KEY, API_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatwootWebhook_sinJwt_noEsRechazadoPorLaCadenaDeSeguridad() throws Exception {
        //Sin secret en el perfil de test el webhook acepta el body y, al no ser un mensaje del bot, lo ignora
        mockMvc.perform(post("/accesmed-api/Chatwoot/Webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"conversation_created\"}"))
                .andExpect(status().isOk());
    }

}
