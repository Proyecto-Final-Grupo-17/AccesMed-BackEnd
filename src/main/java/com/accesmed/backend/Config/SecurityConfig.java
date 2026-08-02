package com.accesmed.backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración mínima de Spring Security para que la app arranque. Sin JWT todavía: eso
 * lo trae la feature de Security (ver {@code docs/ARQUITECTURA.md}). Acá solo se abren los
 * endpoints públicos de infraestructura (Swagger, health) y se deja el resto autenticado.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    //region ========== Métodos ==========

    /**
     * Define la cadena de filtros de seguridad base: público solo Swagger y el health
     * check, el resto requiere autenticación.
     *
     * @param httpSecurity {@code HttpSecurity} el builder de configuración HTTP de Spring Security
     * @return {@code SecurityFilterChain} la cadena de filtros configurada
     * @throws Exception {@code Exception} si falla la construcción de la cadena de filtros
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        httpSecurity
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health", "/accesmed-api/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();

    }

    //endregion

}
