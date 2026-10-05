package com.accesmed.backend.Security.Services.Utils;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitarios de {@link AlcanceMedicoService}: resolución del scope de lectura
 * (`resolveMedicoId`) y validación de propiedad en escritura (`validateMedicoPropietario`)
 * para un usuario médico vs. un usuario admin.
 */
class AlcanceMedicoServiceTest {

    private final AlcanceMedicoService alcanceMedicoService = new AlcanceMedicoService();

    private UUID medicoId;
    private UsuarioDetails usuarioMedico;
    private UsuarioDetails usuarioAdmin;

    @BeforeEach
    void setUp() {
        medicoId = UUID.randomUUID();

        Medico medico = new Medico();
        medico.setId(medicoId);
        Usuario usuarioConMedico = new Usuario();
        usuarioConMedico.setId(UUID.randomUUID());
        usuarioConMedico.setMedico(medico);
        usuarioMedico = new UsuarioDetails(usuarioConMedico, List.of());

        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        Usuario usuarioConAdmin = new Usuario();
        usuarioConAdmin.setId(UUID.randomUUID());
        usuarioConAdmin.setAdmin(admin);
        usuarioAdmin = new UsuarioDetails(usuarioConAdmin, List.of());
    }

    @Test
    void resolveMedicoId_usuarioEsMedico_ignoraElSolicitadoYFuerzaElPropio() {
        UUID medicoIdSolicitado = UUID.randomUUID();

        UUID resuelto = alcanceMedicoService.resolveMedicoId(usuarioMedico, medicoIdSolicitado);

        assertEquals(medicoId, resuelto);
    }

    @Test
    void resolveMedicoId_usuarioNoEsMedico_devuelveElSolicitadoTalCual() {
        UUID medicoIdSolicitado = UUID.randomUUID();

        UUID resuelto = alcanceMedicoService.resolveMedicoId(usuarioAdmin, medicoIdSolicitado);

        assertEquals(medicoIdSolicitado, resuelto);
    }

    @Test
    void resolveMedicoId_usuarioNoEsMedicoYNoSolicitaNinguno_devuelveNull() {
        UUID resuelto = alcanceMedicoService.resolveMedicoId(usuarioAdmin, null);

        assertEquals(null, resuelto);
    }

    @Test
    void validateMedicoPropietario_medicoDuenoDelRecurso_noLanza() {
        alcanceMedicoService.validateMedicoPropietario(usuarioMedico, medicoId);
    }

    @Test
    void validateMedicoPropietario_medicoAjenoAlRecurso_lanzaReglaNegocio() {
        UUID medicoIdDeOtro = UUID.randomUUID();

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> alcanceMedicoService.validateMedicoPropietario(usuarioMedico, medicoIdDeOtro));

        assertEquals("RECURSO_AJENO", excepcion.getCodigo());
    }

    @Test
    void validateMedicoPropietario_usuarioNoEsMedico_noRestringeNada() {
        alcanceMedicoService.validateMedicoPropietario(usuarioAdmin, UUID.randomUUID());
    }

}
