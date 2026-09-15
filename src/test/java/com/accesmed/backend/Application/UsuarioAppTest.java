package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Services.DomainServices.RolDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link UsuarioApp}: baja directa (con y sin motivo) y preparación de
 * cambio de mail (mail disponible, igual al actual, ya registrado por otro usuario).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioAppTest {

    @Mock
    private UsuarioDomainService usuarioDomainService;
    @Mock
    private UsuarioRolDomainService usuarioRolDomainService;
    @Mock
    private RolDomainService rolDomainService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioApp usuarioApp;

    private Usuario usuarioActivo;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        usuarioId = UUID.randomUUID();
        usuarioActivo = new Usuario();
        usuarioActivo.setId(usuarioId);
        usuarioActivo.setMail("medico@accesmed.com");
        usuarioActivo.setAdmin(new Admin());
    }

    @Test
    void softDeleteUsuarioDirecto_conMotivo_usaElMotivoProvisto() {

        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuarioActivo);

        usuarioApp.softDeleteUsuarioDirecto(usuarioId, "Cuenta comprometida.");

        verify(usuarioDomainService).softDeleteUsuario(usuarioActivo, "Cuenta comprometida.");

    }

    @Test
    void softDeleteUsuarioDirecto_sinMotivo_usaElMotivoPorDefault() {

        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuarioActivo);

        usuarioApp.softDeleteUsuarioDirecto(usuarioId, null);

        verify(usuarioDomainService).softDeleteUsuario(eq(usuarioActivo), anyString());
        verify(usuarioDomainService).softDeleteUsuario(usuarioActivo, "Baja manual por SuperAdmin.");

    }

    @Test
    void softDeleteUsuarioDirecto_conMotivoEnBlanco_usaElMotivoPorDefault() {

        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuarioActivo);

        usuarioApp.softDeleteUsuarioDirecto(usuarioId, "   ");

        verify(usuarioDomainService).softDeleteUsuario(usuarioActivo, "Baja manual por SuperAdmin.");

    }

    @Test
    void prepararCambioMail_conMailNuevoDisponible_noLanzaExcepcion() {

        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuarioActivo);
        when(usuarioDomainService.findUsuarioActivoByMail("nuevo@accesmed.com")).thenReturn(Optional.empty());

        Usuario resultado = usuarioApp.prepararCambioMail(usuarioId, "nuevo@accesmed.com");

        assertEquals(usuarioActivo, resultado);
        assertEquals("medico@accesmed.com", resultado.getMail());

    }

    @Test
    void prepararCambioMail_conMailNuevoIgualAlActual_lanzaReglaNegocio() {

        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuarioActivo);

        ReglaNegocioException exception = assertThrows(ReglaNegocioException.class,
                () -> usuarioApp.prepararCambioMail(usuarioId, "medico@accesmed.com"));

        assertEquals("MAIL_YA_REGISTRADO", exception.getCodigo());
        verify(usuarioDomainService, never()).findUsuarioActivoByMail(anyString());

    }

    @Test
    void prepararCambioMail_conMailNuevoYaRegistradoPorOtroUsuario_lanzaReglaNegocio() {

        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(UUID.randomUUID());

        when(usuarioDomainService.findUsuarioActivoById(usuarioId)).thenReturn(usuarioActivo);
        when(usuarioDomainService.findUsuarioActivoByMail("nuevo@accesmed.com")).thenReturn(Optional.of(otroUsuario));

        ReglaNegocioException exception = assertThrows(ReglaNegocioException.class,
                () -> usuarioApp.prepararCambioMail(usuarioId, "nuevo@accesmed.com"));

        assertEquals("MAIL_YA_REGISTRADO", exception.getCodigo());

    }

}
