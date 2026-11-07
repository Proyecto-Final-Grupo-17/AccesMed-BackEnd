package com.accesmed.backend.Security.Config;

import com.accesmed.backend.Security.Agente.AgenteApiKeyFilter;
import com.accesmed.backend.Security.Jwt.JwtAuthenticationEntryPoint;
import com.accesmed.backend.Security.Jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.Assert;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuración real de Spring Security: JWT stateless, con recálculo de permisos por
 * request. Reemplaza al placeholder anterior ({@code Config/SecurityConfig.java}).
 * La documentación (Swagger UI y OpenAPI) va en una cadena aparte con HTTP Basic.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityFilterChainConfig {

    //region ========== Dependencias o inyecciones ==========

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    //endregion

    //region ========== Métodos ==========

    /**
     * docsSecurityFilterChain: protege Swagger UI y el OpenAPI con HTTP Basic, con un usuario
     * propio de la documentación que no es usuario del sistema ni sirve contra la API.
     *
     * @param httpSecurity {@code HttpSecurity} builder de la cadena.
     * @param docsUsername {@code String} usuario de la documentación ({@code accesmed.docs.username}).
     * @param docsPassword {@code String} contraseña de la documentación ({@code accesmed.docs.password}).
     * @return {@code SecurityFilterChain} cadena que atiende solo las rutas de la documentación.
     * @throws IllegalArgumentException si la contraseña de la documentación está vacía.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain docsSecurityFilterChain(HttpSecurity httpSecurity,
            @Value("${accesmed.docs.username}") String docsUsername,
            @Value("${accesmed.docs.password}") String docsPassword) throws Exception {

        //Sin contraseña la documentación quedaría abierta: se corta el arranque en vez de exponerla
        Assert.hasText(docsPassword, "accesmed.docs.password (ACCESMED_DOCS_PASSWORD) no puede estar vacía");

        //Usuario en memoria con su propio AuthenticationManager: no pasa por UsuarioDetailsService,
        //así que ni existe en la base ni puede autenticarse contra /accesmed-api
        UserDetails usuarioDocs = User.withUsername(docsUsername)
                .password(passwordEncoder().encode(docsPassword))
                .roles("DOCS")
                .build();
        DaoAuthenticationProvider docsAuthenticationProvider =
                new DaoAuthenticationProvider(new InMemoryUserDetailsManager(usuarioDocs));
        docsAuthenticationProvider.setPasswordEncoder(passwordEncoder());

        //Solo atiende las rutas de la documentación; el resto sigue a la cadena JWT
        httpSecurity
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .authenticationManager(new ProviderManager(docsAuthenticationProvider))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        SecurityFilterChain docsSecurityFilterChain = httpSecurity.build();
        return docsSecurityFilterChain;

    }

    /**
     * agenteSecurityFilterChain: protege los endpoints del canal chatbot ({@code /accesmed-api/Agente/**})
     * con la API key compartida con Flowise ({@code X-Agente-Api-Key}). Stateless, sin JWT ni permisos:
     * la autorización real es el scope por teléfono que aplica {@code AgenteApp}.
     *
     * @param httpSecurity {@code HttpSecurity} builder de la cadena.
     * @param agenteApiKey {@code String} clave del agente ({@code accesmed.agente.api-key}).
     * @param objectMapper {@code ObjectMapper} serializador del {@code AccesMedError} del 401.
     * @return {@code SecurityFilterChain} cadena que atiende solo las rutas del agente.
     * @throws IllegalArgumentException si la clave del agente está vacía.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain agenteSecurityFilterChain(HttpSecurity httpSecurity,
            @Value("${accesmed.agente.api-key}") String agenteApiKey,
            ObjectMapper objectMapper) throws Exception {

        //Sin clave el canal quedaría abierto: se corta el arranque en vez de exponerlo
        Assert.hasText(agenteApiKey, "accesmed.agente.api-key (ACCESMED_AGENTE_API_KEY) no puede estar vacía");

        httpSecurity
                .securityMatcher("/accesmed-api/Agente/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(new AgenteApiKeyFilter(agenteApiKey, objectMapper), UsernamePasswordAuthenticationFilter.class);

        SecurityFilterChain agenteSecurityFilterChain = httpSecurity.build();
        return agenteSecurityFilterChain;

    }

    /**
     * chatwootSecurityFilterChain: deja pasar el webhook de Chatwoot ({@code /accesmed-api/Chatwoot/**})
     * sin JWT. La autenticidad la valida {@code ChatwootApp} con la firma HMAC del request, porque
     * necesita el body crudo, que no está disponible en un filtro sin consumirlo.
     *
     * @param httpSecurity {@code HttpSecurity} builder de la cadena.
     * @return {@code SecurityFilterChain} cadena que atiende solo las rutas del webhook.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain chatwootSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {

        httpSecurity
                .securityMatcher("/accesmed-api/Chatwoot/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        SecurityFilterChain chatwootSecurityFilterChain = httpSecurity.build();
        return chatwootSecurityFilterChain;

    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        httpSecurity
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health",
                                "/accesmed-api/Auth/Login", "/accesmed-api/Auth/Refresh",
                                "/accesmed-api/Auth/OlvideContrasena", "/accesmed-api/Auth/RestablecerContrasena",
                                "/accesmed-api/Auth/ConfirmarCambioMail")
                        .permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();

    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    //endregion

}
