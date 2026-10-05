package com.accesmed.backend.Security.Jwt;

import com.accesmed.backend.Domain.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

/**
 * Adapter entre {@link Usuario} (entidad de dominio) y {@link UserDetails} (lo que
 * Spring Security necesita). Además de lo estándar, expone {@code medicoId}/{@code adminId}
 * para que los servicios de alcance ({@code AlcanceMedicoService}) sepan si quien llama
 * es un médico y a cuál corresponde, sin volver a consultar la base.
 */
public class UsuarioDetails implements UserDetails {

    //region ========== Atributos ==========

    private final Usuario usuario;
    private final Collection<? extends GrantedAuthority> authorities;

    //endregion

    //region ========== Métodos ==========

    public UsuarioDetails(Usuario usuario, Collection<? extends GrantedAuthority> authorities) {
        this.usuario = usuario;
        this.authorities = authorities;
    }

    /**
     * @return {@code UUID} id del usuario autenticado
     */
    public UUID getUsuarioId() {
        return usuario.getId();
    }

    /**
     * @return {@code UUID} id del médico vinculado, o {@code null} si el usuario no es médico
     */
    public UUID getMedicoId() {
        return usuario.getMedico() != null ? usuario.getMedico().getId() : null;
    }

    /**
     * @return {@code UUID} id del admin vinculado, o {@code null} si el usuario no es admin
     */
    public UUID getAdminId() {
        return usuario.getAdmin() != null ? usuario.getAdmin().getId() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return usuario.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return usuario.getMail();
    }

    @Override
    public boolean isEnabled() {
        return usuario.getDeletedAt() == null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    //endregion

}
