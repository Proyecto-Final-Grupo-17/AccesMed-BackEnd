package com.accesmed.backend.Security.Controllers;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Repositories.AdminRepository;
import com.accesmed.backend.Repositories.RolRepository;
import com.accesmed.backend.Repositories.UsuarioRepository;
import com.accesmed.backend.Repositories.UsuarioRolRepository;
import com.accesmed.backend.TestcontainersConfiguration;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración de {@link AuthController} contra el contexto real de Spring (Postgres
 * efímera vía Testcontainers, filter chain de seguridad real, Liquibase real): login
 * válido/inválido, refresh, y un endpoint protegido con 401 (sin token) y 403 (autenticado
 * sin el permiso). Semilla su propio {@code Admin}+{@code Usuario}+rol "Admin" en cada test
 * ({@code @Transactional} hace rollback al terminar, así que no ensucia otros tests).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerTest {

    private static final String PASSWORD = "Password123!";
    private static final String MAIL = "admin.test@accesmed.local";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private UsuarioRolRepository usuarioRolRepository;

    @BeforeEach
    void setUp() {
        Admin admin = new Admin();
        admin.setNombre("Test");
        admin.setApellido("Admin");
        admin.setDni("11111111");
        admin.setEmail(MAIL);
        admin = adminRepository.save(admin);

        Usuario usuario = new Usuario();
        usuario.setMail(MAIL);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD));
        usuario.setAdmin(admin);
        usuario = usuarioRepository.save(usuario);

        Rol rolAdmin = rolRepository.findByNombreAndDeletedAtIsNull("Admin").orElseThrow();

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rolAdmin);
        usuarioRol.setFechaInicioVigencia(ZonedDateTime.now().minusMinutes(1));
        usuarioRolRepository.save(usuarioRol);
    }

    @Test
    void login_credencialesValidas_devuelveAccessYRefreshToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestJson(MAIL, PASSWORD));

        mockMvc.perform(post("/accesmed-api/Auth/Login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void login_passwordIncorrecta_401CredencialesInvalidas() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestJson(MAIL, "password-incorrecta"));

        mockMvc.perform(post("/accesmed-api/Auth/Login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void refresh_conRefreshTokenDelLoginPrevio_devuelveAccessTokenNuevo() throws Exception {
        String loginBody = objectMapper.writeValueAsString(new LoginRequestJson(MAIL, PASSWORD));

        String loginResponseJson = mockMvc.perform(post("/accesmed-api/Auth/Login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponseJson).get("refreshToken").asText();
        String refreshBody = objectMapper.writeValueAsString(new RefreshRequestJson(refreshToken));

        mockMvc.perform(post("/accesmed-api/Auth/Refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void endpointProtegido_sinToken_401NoAutenticado() throws Exception {
        mockMvc.perform(get("/accesmed-api/Rol/Rol"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void endpointProtegido_autenticadoSinElPermiso_403AccesoDenegado() throws Exception {
        String loginBody = objectMapper.writeValueAsString(new LoginRequestJson(MAIL, PASSWORD));

        String loginResponseJson = mockMvc.perform(post("/accesmed-api/Auth/Login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(loginResponseJson).get("accessToken").asText();

        // El rol "Admin" no tiene AUTZ_ROL_CONSULTAR (exclusivo de SuperAdmin): 403.
        mockMvc.perform(get("/accesmed-api/Rol/Rol")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    private record LoginRequestJson(String mail, String password) {
    }

    private record RefreshRequestJson(String refreshToken) {
    }

}
