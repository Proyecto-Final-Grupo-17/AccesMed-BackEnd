package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Services.DomainServices.RolDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link UsuarioApp}: alta de usuario pendiente de activación para un
 * médico y para un admin (con baja del anterior si ya tenía uno y asignación del rol de
 * sistema correspondiente), y desactivación por médico/admin.
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

    @Test
    void crearUsuarioPendienteActivacion_paraMedicoSinUsuarioPrevio_creaYAsignaRolMedico() {
        UUID medicoId = UUID.randomUUID();
        Rol rolMedico = new Rol();
        rolMedico.setId(UUID.randomUUID());
        rolMedico.setNombre("Medico");

        when(passwordEncoder.encode(anyString())).thenReturn("hash-inicial-no-usable");
        when(usuarioDomainService.findUsuarioActivoByMedicoId(medicoId)).thenReturn(Optional.empty());
        when(usuarioDomainService.saveUsuario(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(rolDomainService.findRolActivoByNombre("Medico")).thenReturn(rolMedico);

        Usuario usuarioCreado = usuarioApp.crearUsuarioPendienteActivacion(medicoId, null, "medico@accesmed.local");

        assertNotNull(usuarioCreado);
        assertEquals("medico@accesmed.local", usuarioCreado.getMail());
        assertEquals(medicoId, usuarioCreado.getMedico().getId());
        assertNull(usuarioCreado.getAdmin());

        verify(usuarioDomainService, never()).softDeleteUsuario(any(), anyString());
        verify(usuarioRolDomainService).validateAsignacionRol(usuarioCreado, rolMedico);
        verify(usuarioRolDomainService).saveUsuarioRol(any());
    }

    @Test
    void crearUsuarioPendienteActivacion_medicoConUsuarioPrevio_loDaDeBajaAntesDeCrearElNuevo() {
        UUID medicoId = UUID.randomUUID();
        Usuario usuarioAnterior = new Usuario();
        usuarioAnterior.setId(UUID.randomUUID());

        Rol rolMedico = new Rol();
        rolMedico.setNombre("Medico");

        when(passwordEncoder.encode(anyString())).thenReturn("hash-inicial-no-usable");
        when(usuarioDomainService.findUsuarioActivoByMedicoId(medicoId)).thenReturn(Optional.of(usuarioAnterior));
        when(usuarioDomainService.saveUsuario(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(rolDomainService.findRolActivoByNombre("Medico")).thenReturn(rolMedico);

        usuarioApp.crearUsuarioPendienteActivacion(medicoId, null, "medico-nuevo@accesmed.local");

        verify(usuarioDomainService).softDeleteUsuario(eq(usuarioAnterior), anyString());
    }

    @Test
    void crearUsuarioPendienteActivacion_paraAdmin_asignaRolAdmin() {
        UUID adminId = UUID.randomUUID();
        Rol rolAdmin = new Rol();
        rolAdmin.setNombre("Admin");

        when(passwordEncoder.encode(anyString())).thenReturn("hash-inicial-no-usable");
        when(usuarioDomainService.findUsuarioActivoByAdminId(adminId)).thenReturn(Optional.empty());
        when(usuarioDomainService.saveUsuario(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(rolDomainService.findRolActivoByNombre("Admin")).thenReturn(rolAdmin);

        Usuario usuarioCreado = usuarioApp.crearUsuarioPendienteActivacion(null, adminId, "admin@accesmed.local");

        assertEquals(adminId, usuarioCreado.getAdmin().getId());
        assertNull(usuarioCreado.getMedico());
        verify(usuarioRolDomainService).validateAsignacionRol(usuarioCreado, rolAdmin);
    }

    @Test
    void desactivarUsuarioPorMedico_conUsuarioActivo_loDaDeBaja() {
        UUID medicoId = UUID.randomUUID();
        Usuario usuarioActivo = new Usuario();
        usuarioActivo.setId(UUID.randomUUID());

        when(usuarioDomainService.findUsuarioActivoByMedicoId(medicoId)).thenReturn(Optional.of(usuarioActivo));

        Usuario resultado = usuarioApp.desactivarUsuarioPorMedico(medicoId);

        assertEquals(usuarioActivo, resultado);
        verify(usuarioDomainService).softDeleteUsuario(eq(usuarioActivo), anyString());
    }

    @Test
    void desactivarUsuarioPorMedico_sinUsuarioActivo_noHaceNadaYDevuelveNull() {
        UUID medicoId = UUID.randomUUID();

        when(usuarioDomainService.findUsuarioActivoByMedicoId(medicoId)).thenReturn(Optional.empty());

        Usuario resultado = usuarioApp.desactivarUsuarioPorMedico(medicoId);

        assertNull(resultado);
        verify(usuarioDomainService, never()).softDeleteUsuario(any(), anyString());
    }

    @Test
    void desactivarUsuarioPorAdmin_conUsuarioActivo_loDaDeBaja() {
        UUID adminId = UUID.randomUUID();
        Usuario usuarioActivo = new Usuario();
        usuarioActivo.setId(UUID.randomUUID());

        when(usuarioDomainService.findUsuarioActivoByAdminId(adminId)).thenReturn(Optional.of(usuarioActivo));

        Usuario resultado = usuarioApp.desactivarUsuarioPorAdmin(adminId);

        assertEquals(usuarioActivo, resultado);
        verify(usuarioDomainService).softDeleteUsuario(eq(usuarioActivo), anyString());
    }

}
