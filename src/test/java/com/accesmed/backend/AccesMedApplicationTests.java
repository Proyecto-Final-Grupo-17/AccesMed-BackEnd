package com.accesmed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// La Postgres para correr contra ddl-auto: validate y Liquibase la levanta Testcontainers
// (ver TestcontainersConfiguration), no la de dev — no requiere docker compose corriendo
// a mano, y no comparte datos con la BD de desarrollo.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccesMedApplicationTests {

    @Test
    void contextLoads() {
    }

}
