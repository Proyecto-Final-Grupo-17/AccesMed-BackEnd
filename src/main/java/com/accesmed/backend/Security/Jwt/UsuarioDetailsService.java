package com.accesmed.backend.Security.Jwt;

import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduce un {@link Usuario} del dominio al {@link UserDetails} que necesita Spring
 * Security, recalculando sus permisos ({@code authorities}) desde la base en cada
 * llamada — nunca desde datos cacheados en el token. Así un cambio de rol o de permisos
 * tiene efecto inmediato, sin esperar a que expire el access token.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    //region ========== Dependencias o inyecciones ==========

    private final UsuarioDomainService usuarioDomainService;
    private final UsuarioRolDomainService usuarioRolDomainService;

    //endregion

    //region ========== Métodos ==========

    @Override
    public UserDetails loadUserByUsername(String mail) throws UsernameNotFoundException {

        Usuario usuario = usuarioDomainService.findUsuarioActivoByMail(mail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + mail));

        List<UsuarioRol> rolesVigentes = usuarioRolDomainService.findVigentesByUsuarioId(usuario.getId());

        Set<GrantedAuthority> authorities = rolesVigentes.stream()
                .flatMap(usuarioRol -> usuarioRol.getRol().getPermisos().stream())
                .map(this::toAuthority)
                .collect(Collectors.toSet());

        return new UsuarioDetails(usuario, authorities);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private GrantedAuthority toAuthority(Permiso permiso) {
        return new SimpleGrantedAuthority(permiso.name());
    }

    //endregion

}
