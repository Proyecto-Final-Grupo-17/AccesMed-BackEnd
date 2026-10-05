package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.HistoricoEstadoTurno;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Repositories.HistoricoEstadoTurnoRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link HistoricoEstadoTurnoDomainService}:
 * verifica que las transiciones de estado nuevas validen correctamente
 * el estado vigente (caso feliz y guardas) y persistan el histórico de cambios.
 */
@ExtendWith(MockitoExtension.class)
class HistoricoEstadoTurnoDomainServiceTest {

    @Mock
    private HistoricoEstadoTurnoRepository historicoEstadoTurnoRepository;

    @InjectMocks
    private HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;

    private Turno turno;
    private UUID turnoId;

    @BeforeEach
    void setUp() {
        turno = new Turno();
        turnoId = UUID.randomUUID();
        turno.setId(turnoId);
    }

    // region ========== transitionPendienteToConfirmadoTurno ==========

    @Test
    void transitionPendienteToConfirmadoTurno_estadoVigenteEsPENDIENTE_transicionaSinExcepcion() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.PENDIENTE)));

        assertDoesNotThrow(() -> historicoEstadoTurnoDomainService.transitionPendienteToConfirmadoTurno(turno));

        verify(historicoEstadoTurnoRepository, times(2))
                .findByTurno_IdAndFechaHoraFinIsNull(turnoId);
        verify(historicoEstadoTurnoRepository).saveAndFlush(any(HistoricoEstadoTurno.class));
        verify(historicoEstadoTurnoRepository).save(any(HistoricoEstadoTurno.class));
    }

    @Test
    void transitionPendienteToConfirmadoTurno_estadoVigenteNoEsPENDIENTE_lanzaReglaNegocioException() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.CONFIRMADO)));

        assertThrows(ReglaNegocioException.class,
                () -> historicoEstadoTurnoDomainService.transitionPendienteToConfirmadoTurno(turno));

        verify(historicoEstadoTurnoRepository, never()).saveAndFlush(any());
        verify(historicoEstadoTurnoRepository, never()).save(any());
    }

    // region ========== transitionConfirmadoToEnSalaDeEsperaTurno ==========

    @Test
    void transitionConfirmadoToEnSalaDeEsperaTurno_estadoVigenteEsCONFIRMADO_transicionaSinExcepcion() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.CONFIRMADO)));

        assertDoesNotThrow(() -> historicoEstadoTurnoDomainService.transitionConfirmadoToEnSalaDeEsperaTurno(turno));

        verify(historicoEstadoTurnoRepository, times(2))
                .findByTurno_IdAndFechaHoraFinIsNull(turnoId);
        verify(historicoEstadoTurnoRepository).saveAndFlush(any(HistoricoEstadoTurno.class));
        verify(historicoEstadoTurnoRepository).save(any(HistoricoEstadoTurno.class));
    }

    @Test
    void transitionConfirmadoToEnSalaDeEsperaTurno_estadoVigenteNoEsCONFIRMADO_lanzaReglaNegocioException() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.EN_SALA_DE_ESPERA)));

        assertThrows(ReglaNegocioException.class,
                () -> historicoEstadoTurnoDomainService.transitionConfirmadoToEnSalaDeEsperaTurno(turno));

        verify(historicoEstadoTurnoRepository, never()).saveAndFlush(any());
        verify(historicoEstadoTurnoRepository, never()).save(any());
    }

    // region ========== transitionEnSalaDeEsperaToEnCursoTurno ==========

    @Test
    void transitionEnSalaDeEsperaToEnCursoTurno_estadoVigenteEsEN_SALA_DE_ESPERA_transicionaSinExcepcion() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.EN_SALA_DE_ESPERA)));

        assertDoesNotThrow(() -> historicoEstadoTurnoDomainService.transitionEnSalaDeEsperaToEnCursoTurno(turno));

        verify(historicoEstadoTurnoRepository, times(2))
                .findByTurno_IdAndFechaHoraFinIsNull(turnoId);
        verify(historicoEstadoTurnoRepository).saveAndFlush(any(HistoricoEstadoTurno.class));
        verify(historicoEstadoTurnoRepository).save(any(HistoricoEstadoTurno.class));
    }

    @Test
    void transitionEnSalaDeEsperaToEnCursoTurno_estadoVigenteNoEsEN_SALA_DE_ESPERA_lanzaReglaNegocioException() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.EN_CURSO)));

        assertThrows(ReglaNegocioException.class,
                () -> historicoEstadoTurnoDomainService.transitionEnSalaDeEsperaToEnCursoTurno(turno));

        verify(historicoEstadoTurnoRepository, never()).saveAndFlush(any());
        verify(historicoEstadoTurnoRepository, never()).save(any());
    }

    // region ========== transitionEnCursoToFinalizadoTurno ==========

    @Test
    void transitionEnCursoToFinalizadoTurno_estadoVigenteEsEN_CURSO_transicionaSinExcepcion() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.EN_CURSO)));

        assertDoesNotThrow(() -> historicoEstadoTurnoDomainService.transitionEnCursoToFinalizadoTurno(turno));

        verify(historicoEstadoTurnoRepository, times(2))
                .findByTurno_IdAndFechaHoraFinIsNull(turnoId);
        verify(historicoEstadoTurnoRepository).saveAndFlush(any(HistoricoEstadoTurno.class));
        verify(historicoEstadoTurnoRepository).save(any(HistoricoEstadoTurno.class));
    }

    @Test
    void transitionEnCursoToFinalizadoTurno_estadoVigenteNoEsEN_CURSO_lanzaReglaNegocioException() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.FINALIZADO)));

        assertThrows(ReglaNegocioException.class,
                () -> historicoEstadoTurnoDomainService.transitionEnCursoToFinalizadoTurno(turno));

        verify(historicoEstadoTurnoRepository, never()).saveAndFlush(any());
        verify(historicoEstadoTurnoRepository, never()).save(any());
    }

    // region ========== transitionToAusenteTurno ==========

    @Test
    void transitionToAusenteTurno_estadoVigenteEsCONFIRMADO_transicionaSinExcepcion() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.CONFIRMADO)));

        assertDoesNotThrow(() -> historicoEstadoTurnoDomainService.transitionToAusenteTurno(turno));

        verify(historicoEstadoTurnoRepository, times(2))
                .findByTurno_IdAndFechaHoraFinIsNull(turnoId);
        verify(historicoEstadoTurnoRepository).saveAndFlush(any(HistoricoEstadoTurno.class));
        verify(historicoEstadoTurnoRepository).save(any(HistoricoEstadoTurno.class));
    }

    @Test
    void transitionToAusenteTurno_estadoVigenteNoEsCONFIRMADO_lanzaReglaNegocioException() {
        when(historicoEstadoTurnoRepository.findByTurno_IdAndFechaHoraFinIsNull(turnoId))
                .thenReturn(Optional.of(mockHistoricoEstadoTurno(EstadoTurno.PENDIENTE)));

        assertThrows(ReglaNegocioException.class,
                () -> historicoEstadoTurnoDomainService.transitionToAusenteTurno(turno));

        verify(historicoEstadoTurnoRepository, never()).saveAndFlush(any());
        verify(historicoEstadoTurnoRepository, never()).save(any());
    }

    // region ========== Métodos auxiliares privados ==========

    private HistoricoEstadoTurno mockHistoricoEstadoTurno(EstadoTurno estado) {
        HistoricoEstadoTurno historico = new HistoricoEstadoTurno();
        historico.setId(UUID.randomUUID());
        historico.setTurno(turno);
        historico.setEstado(estado);
        historico.setFechaHoraInicio(ZonedDateTime.now());
        return historico;
    }

}
