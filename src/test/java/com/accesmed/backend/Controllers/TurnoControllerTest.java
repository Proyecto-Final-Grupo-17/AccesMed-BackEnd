package com.accesmed.backend.Controllers;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Records.Turno.Response.ConfirmTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.FinishTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.StartAtencionTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.StartSalaDeEsperaTurnoResponse;
import com.accesmed.backend.Application.TurnoApp;
import com.accesmed.backend.Services.QueryServices.TurnoQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para {@link TurnoController}:
 * verifica que los 4 métodos de transición manual delegan correctamente en turnoApp.
 */
@ExtendWith(MockitoExtension.class)
class TurnoControllerTest {

    @Mock
    private TurnoApp turnoApp;

    @Mock
    private TurnoQueryService turnoQueryService;

    @InjectMocks
    private TurnoController turnoController;

    @Test
    void confirmTurno_delegaEnTurnoAppYDevuelveRespuesta() {
        UUID turnoId = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID prestacionId = UUID.randomUUID();
        ZonedDateTime fecha = ZonedDateTime.now().plusDays(1);
        BigDecimal monto = BigDecimal.valueOf(100.00);

        ConfirmTurnoResponse respuestaEsperada = new ConfirmTurnoResponse(
                turnoId,
                "TURNO_12345678",
                pacienteId,
                medicoId,
                prestacionId,
                fecha,
                monto,
                "PARTICULAR",
                EstadoTurno.CONFIRMADO
        );

        when(turnoApp.confirmTurno(any(UUID.class))).thenReturn(respuestaEsperada);

        var respuesta = turnoController.confirmTurno(turnoId);

        assertNotNull(respuesta);
        assertNotNull(respuesta.getBody());
        verify(turnoApp).confirmTurno(turnoId);
    }

    @Test
    void startSalaDeEsperaTurno_delegaEnTurnoAppYDevuelveRespuesta() {
        UUID turnoId = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID prestacionId = UUID.randomUUID();
        ZonedDateTime fecha = ZonedDateTime.now().plusDays(1);
        BigDecimal monto = BigDecimal.valueOf(100.00);

        StartSalaDeEsperaTurnoResponse respuestaEsperada = new StartSalaDeEsperaTurnoResponse(
                turnoId,
                "TURNO_12345678",
                pacienteId,
                medicoId,
                prestacionId,
                fecha,
                monto,
                "PARTICULAR",
                EstadoTurno.EN_SALA_DE_ESPERA
        );

        when(turnoApp.startSalaDeEsperaTurno(any(UUID.class))).thenReturn(respuestaEsperada);

        var respuesta = turnoController.startSalaDeEsperaTurno(turnoId);

        assertNotNull(respuesta);
        assertNotNull(respuesta.getBody());
        verify(turnoApp).startSalaDeEsperaTurno(turnoId);
    }

    @Test
    void startAtencionTurno_delegaEnTurnoAppYDevuelveRespuesta() {
        UUID turnoId = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID prestacionId = UUID.randomUUID();
        ZonedDateTime fecha = ZonedDateTime.now().plusDays(1);
        BigDecimal monto = BigDecimal.valueOf(100.00);

        StartAtencionTurnoResponse respuestaEsperada = new StartAtencionTurnoResponse(
                turnoId,
                "TURNO_12345678",
                pacienteId,
                medicoId,
                prestacionId,
                fecha,
                monto,
                "PARTICULAR",
                EstadoTurno.EN_CURSO
        );

        when(turnoApp.startAtencionTurno(any(UUID.class))).thenReturn(respuestaEsperada);

        var respuesta = turnoController.startAtencionTurno(turnoId);

        assertNotNull(respuesta);
        assertNotNull(respuesta.getBody());
        verify(turnoApp).startAtencionTurno(turnoId);
    }

    @Test
    void finishTurno_delegaEnTurnoAppYDevuelveRespuesta() {
        UUID turnoId = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID prestacionId = UUID.randomUUID();
        ZonedDateTime fecha = ZonedDateTime.now().plusDays(1);
        BigDecimal monto = BigDecimal.valueOf(100.00);

        FinishTurnoResponse respuestaEsperada = new FinishTurnoResponse(
                turnoId,
                "TURNO_12345678",
                pacienteId,
                medicoId,
                prestacionId,
                fecha,
                monto,
                "PARTICULAR",
                EstadoTurno.FINALIZADO
        );

        when(turnoApp.finishTurno(any(UUID.class))).thenReturn(respuestaEsperada);

        var respuesta = turnoController.finishTurno(turnoId);

        assertNotNull(respuesta);
        assertNotNull(respuesta.getBody());
        verify(turnoApp).finishTurno(turnoId);
    }

}
