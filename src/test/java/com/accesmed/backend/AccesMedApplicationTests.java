package com.accesmed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Requiere la Postgres local de docker/dev/docker-compose.yml levantada: ddl-auto:
// validate y Liquibase necesitan un esquema real contra el que validar/migrar.
@SpringBootTest
@ActiveProfiles("dev")
class AccesMedApplicationTests {

    @Test
    void contextLoads() {
    }

}
