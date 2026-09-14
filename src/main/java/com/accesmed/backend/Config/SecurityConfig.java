package com.accesmed.backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración mínima de Spring Security para que la app arranque. Sin JWT todavía: eso
 * lo trae la feature de Security (ver {@code docs/ARQUITECTURA.md}). Acá se abren los
 * endpoints públicos de infraestructura (Swagger, health) y, mientras no haya autenticación,
 * también los de la API, y se declara la aplicación como sin estado.
 * El CORS aplicado es el definido en
 * {@link com.accesmed.backend.Controllers.ControllersConfig.CorsConfig}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    //region ========== Métodos ==========

    /**
     * Define la cadena de filtros de seguridad base: deja públicos Swagger, el health check
     * y los endpoints de la API, declara la aplicación sin estado y deshabilita CSRF.
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
                .cors(Customizer.withDefaults())
                //Sin estado: no se crean ni se usan sesiones HTTP, cada request se autentica
                //por sí mismo. Es la condición que hace segura la línea siguiente — si no hay
                //cookie de sesión, no hay nada que un sitio externo pueda reutilizar para
                //falsificar un request en nombre del usuario (CSRF).
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();

    }

    //endregion

}
