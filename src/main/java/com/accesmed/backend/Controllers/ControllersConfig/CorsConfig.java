package com.accesmed.backend.Controllers.ControllersConfig;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración CORS de la API: qué orígenes pueden llamar a los endpoints de
 * {@code /accesmed-api/**} (panel web interno, herramientas de desarrollo). La lista de
 * orígenes permitidos se define por perfil en {@code application-<perfil>.yml}
 * (propiedad {@code accesmed.cors.allowed-origins}), nunca hardcodeada en el código.
 */
@Configuration
public class CorsConfig {

    //region ========== Atributos ==========

    private final List<String> allowedOrigins;

    //endregion

    //region ========== Constructores ==========

    /**
     * @param allowedOrigins {@code List<String>} orígenes permitidos, leídos de
     *        {@code accesmed.cors.allowed-origins}
     */
    public CorsConfig(@Value("${accesmed.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    //endregion

    //region ========== Métodos ==========

    /**
     * Define los orígenes, métodos y headers permitidos para llamar a
     * {@code /accesmed-api/**}. La usa
     * {@link com.accesmed.backend.Security.Config.SecurityFilterChainConfig}
     * al construir la cadena de filtros.
     *
     * @return {@code CorsConfigurationSource} la configuración CORS aplicada a la API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(allowedOrigins);
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/accesmed-api/**", corsConfiguration);
        return source;

    }

    //endregion

}
