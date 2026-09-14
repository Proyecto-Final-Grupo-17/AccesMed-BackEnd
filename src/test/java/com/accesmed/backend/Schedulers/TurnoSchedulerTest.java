package com.accesmed.backend.Schedulers;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link TurnoScheduler}:
 * verifica que los métodos @Scheduled filtran correctamente por fecha límite
 * vencida y procesan solo los turnos vencidos, sin cortar el loop si uno falla.
 */
@ExtendWith(MockitoExtension.class)
class TurnoSchedulerTest {

    @Mock
    private TurnoDomainService turnoDomainService;

    @Mock
    private TurnoSchedulerService turnoSchedulerService;

    @InjectMocks
    private TurnoScheduler turnoScheduler;

    private Turno turnoVencido;
    private Turno turnoFuturo;
    private ZonedDateTime ahora;

    @BeforeEach
    void setUp() {
        ahora = ZonedDateTime.now();

        turnoVencido = new Turno();
        turnoVencido.setId(java.util.UUID.randomUUID());
        turnoVencido.setFechaLimiteConfirmacion(ahora.minusHours(1));
        turnoVencido.setFechaLimiteValidacion(ahora.minusHours(2));
        turnoVencido.setFechaLimiteAnuncioTardio(ahora.minusMinutes(30));
        turnoVencido.setFechaHoraRecordatorioConfirmacion(ahora.minusMinutes(5));

        turnoFuturo = new Turno();
        turnoFuturo.setId(java.util.UUID.randomUUID());
        turnoFuturo.setFechaLimiteConfirmacion(ahora.plusHours(1));
        turnoFuturo.setFechaLimiteValidacion(ahora.plusHours(2));
        turnoFuturo.setFechaLimiteAnuncioTardio(ahora.plusMinutes(30));
        turnoFuturo.setFechaHoraRecordatorioConfirmacion(ahora.plusMinutes(5));
    }

    // region ========== confirmarTurnosVencidos ==========

    @Test
    void confirmarTurnosVencidos_soloConfirmaLosTurnosConFechaVencida() {
        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE))
                .thenReturn(List.of(turnoVencido, turnoFuturo));
        doNothing().when(turnoSchedulerService).confirmarTurno(any());

        turnoScheduler.confirmarTurnosVencidos();

        verify(turnoSchedulerService, times(1)).confirmarTurno(turnoVencido);
        verify(turnoSchedulerService, never()).confirmarTurno(turnoFuturo);
    }

    @Test
    void confirmarTurnosVencidos_siUnTurnoFallaContinuaConElResto() {
        Turno turnoVencido2 = new Turno();
        turnoVencido2.setId(java.util.UUID.randomUUID());
        turnoVencido2.setFechaLimiteConfirmacion(ahora.minusHours(1));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE))
                .thenReturn(List.of(turnoVencido, turnoVencido2));

        doThrow(new RuntimeException("Simulated failure"))
                .when(turnoSchedulerService).confirmarTurno(turnoVencido);
        doNothing().when(turnoSchedulerService).confirmarTurno(turnoVencido2);

        turnoScheduler.confirmarTurnosVencidos();

        verify(turnoSchedulerService, times(1)).confirmarTurno(turnoVencido);
        verify(turnoSchedulerService, times(1)).confirmarTurno(turnoVencido2);
    }

    // region ========== vencerValidacionesTurno ==========

    @Test
    void vencerValidacionesTurno_soloVenceLosTurnosConFechaVencida() {
        turnoVencido.setFechaLimiteValidacion(ahora.minusHours(2));
        turnoFuturo.setFechaLimiteValidacion(ahora.plusHours(2));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.ESPERA_VALIDACION))
                .thenReturn(List.of(turnoVencido, turnoFuturo));
        doNothing().when(turnoSchedulerService).vencerValidacionTurno(any());

        turnoScheduler.vencerValidacionesTurno();

        verify(turnoSchedulerService, times(1)).vencerValidacionTurno(turnoVencido);
        verify(turnoSchedulerService, never()).vencerValidacionTurno(turnoFuturo);
    }

    @Test
    void vencerValidacionesTurno_siUnTurnoFallaContinuaConElResto() {
        Turno turnoVencido2 = new Turno();
        turnoVencido2.setId(java.util.UUID.randomUUID());
        turnoVencido2.setFechaLimiteValidacion(ahora.minusHours(1));
        turnoVencido2.setFechaLimiteConfirmacion(ahora.plusHours(1));
        turnoVencido2.setFechaLimiteAnuncioTardio(ahora.plusMinutes(30));
        turnoVencido2.setFechaHoraRecordatorioConfirmacion(ahora.plusMinutes(5));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.ESPERA_VALIDACION))
                .thenReturn(List.of(turnoVencido, turnoVencido2));

        doThrow(new RuntimeException("Simulated failure"))
                .when(turnoSchedulerService).vencerValidacionTurno(turnoVencido);
        doNothing().when(turnoSchedulerService).vencerValidacionTurno(turnoVencido2);

        turnoScheduler.vencerValidacionesTurno();

        verify(turnoSchedulerService, times(1)).vencerValidacionTurno(turnoVencido);
        verify(turnoSchedulerService, times(1)).vencerValidacionTurno(turnoVencido2);
    }

    // region ========== marcarAusentesTurno ==========

    @Test
    void marcarAusentesTurno_soloMarcaLosTurnosConFechaVencida() {
        turnoVencido.setFechaLimiteAnuncioTardio(ahora.minusMinutes(30));
        turnoFuturo.setFechaLimiteAnuncioTardio(ahora.plusMinutes(30));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.CONFIRMADO))
                .thenReturn(List.of(turnoVencido, turnoFuturo));
        doNothing().when(turnoSchedulerService).marcarAusenteTurno(any());

        turnoScheduler.marcarAusentesTurno();

        verify(turnoSchedulerService, times(1)).marcarAusenteTurno(turnoVencido);
        verify(turnoSchedulerService, never()).marcarAusenteTurno(turnoFuturo);
    }

    @Test
    void marcarAusentesTurno_siUnTurnoFallaContinuaConElResto() {
        Turno turnoVencido2 = new Turno();
        turnoVencido2.setId(java.util.UUID.randomUUID());
        turnoVencido2.setFechaLimiteAnuncioTardio(ahora.minusMinutes(15));
        turnoVencido2.setFechaLimiteConfirmacion(ahora.plusHours(1));
        turnoVencido2.setFechaLimiteValidacion(ahora.plusHours(2));
        turnoVencido2.setFechaHoraRecordatorioConfirmacion(ahora.plusMinutes(5));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.CONFIRMADO))
                .thenReturn(List.of(turnoVencido, turnoVencido2));

        doThrow(new RuntimeException("Simulated failure"))
                .when(turnoSchedulerService).marcarAusenteTurno(turnoVencido);
        doNothing().when(turnoSchedulerService).marcarAusenteTurno(turnoVencido2);

        turnoScheduler.marcarAusentesTurno();

        verify(turnoSchedulerService, times(1)).marcarAusenteTurno(turnoVencido);
        verify(turnoSchedulerService, times(1)).marcarAusenteTurno(turnoVencido2);
    }

    // region ========== recordarConfirmacionTurno ==========

    @Test
    void recordarConfirmacionTurno_soloRecuerdaLosTurnosConFechaVencida() {
        turnoVencido.setFechaHoraRecordatorioConfirmacion(ahora.minusMinutes(5));
        turnoFuturo.setFechaHoraRecordatorioConfirmacion(ahora.plusMinutes(5));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE))
                .thenReturn(List.of(turnoVencido, turnoFuturo));
        doNothing().when(turnoSchedulerService).recordarConfirmacionTurno(any());

        turnoScheduler.recordarConfirmacionTurno();

        verify(turnoSchedulerService, times(1)).recordarConfirmacionTurno(turnoVencido);
        verify(turnoSchedulerService, never()).recordarConfirmacionTurno(turnoFuturo);
    }

    @Test
    void recordarConfirmacionTurno_siUnTurnoFallaContinuaConElResto() {
        Turno turnoVencido2 = new Turno();
        turnoVencido2.setId(java.util.UUID.randomUUID());
        turnoVencido2.setFechaHoraRecordatorioConfirmacion(ahora.minusMinutes(10));
        turnoVencido2.setFechaLimiteConfirmacion(ahora.plusHours(1));
        turnoVencido2.setFechaLimiteValidacion(ahora.plusHours(2));
        turnoVencido2.setFechaLimiteAnuncioTardio(ahora.plusMinutes(30));

        when(turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE))
                .thenReturn(List.of(turnoVencido, turnoVencido2));

        doThrow(new RuntimeException("Simulated failure"))
                .when(turnoSchedulerService).recordarConfirmacionTurno(turnoVencido);
        doNothing().when(turnoSchedulerService).recordarConfirmacionTurno(turnoVencido2);

        turnoScheduler.recordarConfirmacionTurno();

        verify(turnoSchedulerService, times(1)).recordarConfirmacionTurno(turnoVencido);
        verify(turnoSchedulerService, times(1)).recordarConfirmacionTurno(turnoVencido2);
    }

}
