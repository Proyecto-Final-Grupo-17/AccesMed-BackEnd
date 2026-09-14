package com.accesmed.backend.Security.Services.Utils;

import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Exige un permiso adicional, condicional, dentro de un método — para operaciones que
 * son opcionales y más sensibles que el permiso base del endpoint (ej. crear un médico
 * es {@code MED_ALTA}, pero crearlo con usuario además exige {@code USER_ALTA}). Tira el
 * mismo tipo de excepción que usa Spring Security para {@code @PreAuthorize}
 * ({@link AccessDeniedException}), así ambos mecanismos terminan en el mismo 403.
 */
@Slf4j
@Component
public class AutorizacionService {

    //region ========== Métodos ==========

    /**
     * Exige que el usuario autenticado tenga el permiso indicado.
     *
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @param permiso {@code Permiso} el permiso requerido
     * @throws AccessDeniedException {@code AccessDeniedException} si no lo tiene
     */
    public void requireAuthority(UsuarioDetails usuarioDetails, Permiso permiso) {

        boolean tienePermiso = usuarioDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(permiso.name()));

        if (!tienePermiso) {
            log.warn("Usuario sin el permiso requerido: usuarioId={}, permiso={}",
                    usuarioDetails.getUsuarioId(), permiso);
            throw new AccessDeniedException("No tiene el permiso requerido: " + permiso.name());
        }

    }

    //endregion

}
