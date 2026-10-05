package com.accesmed.backend.Security.Config;

import com.accesmed.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración de la cadena de la documentación ({@code docsSecurityFilterChain} en
 * {@link SecurityFilterChainConfig}): Swagger UI y el OpenAPI piden HTTP Basic con el usuario
 * de {@code accesmed.docs.*} (ver {@code application-test.yml}), el health sigue público y
 * ese usuario no sirve contra la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocsSecurityTest {

    private static final String DOCS_USERNAME = "docs-test";
    private static final String DOCS_PASSWORD = "docs-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_sinCredenciales_401() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUi_sinCredenciales_401() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocs_passwordIncorrecta_401() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic(DOCS_USERNAME, "incorrecta")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocs_credencialesDocs_200() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic(DOCS_USERNAME, DOCS_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void health_sinCredenciales_sigue200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void api_conCredencialesDocs_401() throws Exception {
        mockMvc.perform(get("/accesmed-api/Turno/Turno").with(httpBasic(DOCS_USERNAME, DOCS_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

}
