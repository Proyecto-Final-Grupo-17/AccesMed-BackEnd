package com.accesmed.backend.Config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata de la documentación Swagger/OpenAPI expuesta en {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    //region ========== Métodos ==========

    /**
     * Define el título, versión y descripción que se muestran en Swagger UI.
     *
     * @return {@code OpenAPI} la configuración de metadata de la documentación
     */
    @Bean
    public OpenAPI accesMedOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AccesMed API")
                .version("v1")
                .description("API REST para la gestión de turnos de AccesMed."));
    }

    //endregion

}
