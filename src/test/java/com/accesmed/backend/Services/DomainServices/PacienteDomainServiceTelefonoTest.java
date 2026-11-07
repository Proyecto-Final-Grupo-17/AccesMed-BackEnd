package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Repositories.PacienteRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de las búsquedas por teléfono de {@link PacienteDomainService} (identidad del
 * canal chatbot): se consulta siempre por los dígitos del número.
 */
@ExtendWith(MockitoExtension.class)
class PacienteDomainServiceTelefonoTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private PacienteDomainService pacienteDomainService;

    @Test
    void findPacienteActivoByNumeroTelefono_buscaPorLosDigitosDelNumeroE164() {
        Paciente paciente = new Paciente();
        when(pacienteRepository.findActivoByNumeroTelefonoNormalizado("5492617742682")).thenReturn(Optional.of(paciente));

        Paciente encontrado = pacienteDomainService.findPacienteActivoByNumeroTelefono("+5492617742682");

        assertSame(paciente, encontrado);
        verify(pacienteRepository).findActivoByNumeroTelefonoNormalizado("5492617742682");
    }

    @Test
    void findPacienteActivoByNumeroTelefono_telefonoDesconocido_lanzaPacienteNoEncontrado() {
        when(pacienteRepository.findActivoByNumeroTelefonoNormalizado("5492617742682")).thenReturn(Optional.empty());

        RecursoNoEncontradoException excepcion = assertThrows(RecursoNoEncontradoException.class,
                () -> pacienteDomainService.findPacienteActivoByNumeroTelefono("+5492617742682"));

        assertEquals("PACIENTE_NO_ENCONTRADO", excepcion.getCodigo());
        assertEquals(404, excepcion.getHttpStatus());
    }

    @Test
    void findPacienteActivoByNumeroTelefono_telefonoInvalido_lanzaValidacionSinConsultarLaBase() {
        assertThrows(ValidacionException.class, () -> pacienteDomainService.findPacienteActivoByNumeroTelefono(null));
        assertThrows(ValidacionException.class, () -> pacienteDomainService.findPacienteActivoByNumeroTelefono("abc"));

        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void validateNumeroTelefonoPacienteNormalizadoIsUnique_yaRegistrado_lanzaTelefonoDuplicado() {
        when(pacienteRepository.findActivoByNumeroTelefonoNormalizado("5492617742682"))
                .thenReturn(Optional.of(new Paciente()));

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> pacienteDomainService.validateNumeroTelefonoPacienteNormalizadoIsUnique("+54 9 261 774 2682"));

        assertEquals("PACIENTE_NUMERO_TELEFONO_DUPLICADO", excepcion.getCodigo());
    }

    @Test
    void validateNumeroTelefonoPacienteNormalizadoIsUnique_libre_noLanza() {
        when(pacienteRepository.findActivoByNumeroTelefonoNormalizado("5492617742682")).thenReturn(Optional.empty());

        pacienteDomainService.validateNumeroTelefonoPacienteNormalizadoIsUnique("+5492617742682");
    }

}
