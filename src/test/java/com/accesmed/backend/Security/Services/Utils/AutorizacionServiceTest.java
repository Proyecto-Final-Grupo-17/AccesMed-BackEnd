package com.accesmed.backend.Security.Services.Utils;

import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link AutorizacionService}: el permiso extra condicional dentro de
 * un método ({@code requireAuthority}, que lanza) y su variante de solo consulta
 * ({@code hasAuthority}, que no lanza — usada por los {@code QueryService} para decidir si
 * poblar el bloque de auditoría de un Response).
 */
@ExtendWith(MockitoExtension.class)
class AutorizacionServiceTest {

    private final AutorizacionService autorizacionService = new AutorizacionService();

    @Mock
    private UsuarioDetails usuarioDetails;

    @Test
    void requireAuthority_usuarioTienePermiso_noLanza() {
        doReturn(List.of(new SimpleGrantedAuthority(Permiso.USER_ALTA.name()))).when(usuarioDetails).getAuthorities();

        autorizacionService.requireAuthority(usuarioDetails, Permiso.USER_ALTA);
    }

    @Test
    void requireAuthority_usuarioSinElPermiso_lanzaAccessDenied() {
        doReturn(List.of(new SimpleGrantedAuthority(Permiso.MED_CONSULTAR.name()))).when(usuarioDetails).getAuthorities();
        when(usuarioDetails.getUsuarioId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
                () -> autorizacionService.requireAuthority(usuarioDetails, Permiso.USER_ALTA));
    }

    @Test
    void hasAuthority_usuarioTienePermiso_devuelveTrue() {
        doReturn(List.of(new SimpleGrantedAuthority(Permiso.AUDITORIA_CONSULTAR.name()))).when(usuarioDetails).getAuthorities();

        assertTrue(autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR));
    }

    @Test
    void hasAuthority_usuarioSinElPermiso_devuelveFalseSinLanzar() {
        doReturn(List.of(new SimpleGrantedAuthority(Permiso.PREST_CONSULTAR.name()))).when(usuarioDetails).getAuthorities();

        assertFalse(autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR));
    }

}
