package com.accesmed.backend.Security.Config;

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
