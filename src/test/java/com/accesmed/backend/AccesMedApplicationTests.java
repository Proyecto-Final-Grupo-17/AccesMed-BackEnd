package com.accesmed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// La Postgres para correr contra ddl-auto: validate y Liquibase la levanta Testcontainers
// (ver TestcontainersConfiguration), no la de dev — no requiere docker compose corriendo
// a mano, y no comparte datos con la BD de desarrollo.
// El perfil `test` (src/test/resources/application-test.yml) aporta las properties que el
// contexto necesita y que solo están definidas en los perfiles dev/staging/prod.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccesMedApplicationTests {

    @Test
    void contextLoads() {
    }

}
