package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Repositories.TurnoRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del scope por paciente de {@link TurnoDomainService} (canal chatbot): la
 * búsqueda por código y la validación de pertenencia del turno al paciente.
 */
@ExtendWith(MockitoExtension.class)
class TurnoDomainServicePacienteScopeTest {

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private TurnoDomainService turnoDomainService;

    @Test
    void validateTurnoPerteneceAPaciente_turnoDeOtroPaciente_lanzaRecursoAjeno() {
        Turno turno = turnoDe(pacienteConId(UUID.randomUUID()));

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> turnoDomainService.validateTurnoPerteneceAPaciente(turno, pacienteConId(UUID.randomUUID())));

        assertEquals("RECURSO_AJENO", excepcion.getCodigo());
        assertEquals(409, excepcion.getHttpStatus());
    }

    @Test
    void validateTurnoPerteneceAPaciente_turnoDelMismoPaciente_noLanza() {
        UUID pacienteId = UUID.randomUUID();

        turnoDomainService.validateTurnoPerteneceAPaciente(turnoDe(pacienteConId(pacienteId)), pacienteConId(pacienteId));
    }

    @Test
    void findTurnoByCodigo_existente_devuelveElTurno() {
        Turno turno = new Turno();
        when(turnoRepository.findByCodigo("TURNO_A1B2C3D4")).thenReturn(Optional.of(turno));

        assertSame(turno, turnoDomainService.findTurnoByCodigo("TURNO_A1B2C3D4"));
    }

    @Test
    void findTurnoByCodigo_inexistente_lanzaTurnoNoEncontrado() {
        when(turnoRepository.findByCodigo("TURNO_X")).thenReturn(Optional.empty());

        RecursoNoEncontradoException excepcion = assertThrows(RecursoNoEncontradoException.class,
                () -> turnoDomainService.findTurnoByCodigo("TURNO_X"));

        assertEquals("TURNO_NO_ENCONTRADO", excepcion.getCodigo());
    }

    private Paciente pacienteConId(UUID id) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        return paciente;
    }

    private Turno turnoDe(Paciente paciente) {
        Turno turno = new Turno();
        turno.setCodigo("TURNO_A1B2C3D4");
        turno.setPaciente(paciente);
        return turno;
    }

}
