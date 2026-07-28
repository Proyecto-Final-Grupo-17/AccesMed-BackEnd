package com.accesmed.backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Habilita la auditoría automática de JPA que completa los campos de {@code Auditable}
 * ({@code createdDate}, {@code lastModifiedDate}, {@code createdBy},
 * {@code lastModifiedBy}) al persistir o actualizar cualquier entidad.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    //region ========== Métodos ==========

    /**
     * Provee el usuario actual para {@code createdBy}/{@code lastModifiedBy}.
     * Placeholder hasta que exista la feature de Security (que lo resolverá desde el
     * usuario autenticado): hoy siempre devuelve {@code "system"}.
     *
     * @return {@code AuditorAware<String>} el proveedor de usuario auditor
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of("system");
    }

    //endregion

}
