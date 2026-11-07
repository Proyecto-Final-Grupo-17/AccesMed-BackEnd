package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Response.ListHorarioDisponibleAgenteResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitarios de {@link AgenteMapper}: el {@code slotId} opaco, la fecha-hora combinada y la
 * franja del día de cada horario disponible.
 */
class AgenteMapperTest {

    private final AgenteMapper agenteMapper = Mappers.getMapper(AgenteMapper.class);

    @Test
    void toListHorarioDisponibleResponse_mapeaSlotIdFechaHoraYFranja() {
        UUID slotId = UUID.randomUUID();
        LocalDate fecha = LocalDate.of(2026, 12, 1);

        ListHorarioDisponibleAgenteResponse respuesta = agenteMapper.toListHorarioDisponibleResponse(
                horario(slotId, fecha, LocalTime.of(10, 30)));

        assertEquals(slotId, respuesta.slotId());
        assertEquals(LocalDateTime.of(2026, 12, 1, 10, 30), respuesta.fechaHora());
        assertEquals(FranjaHorariaAgente.MANANA, respuesta.franja());
    }

    @Test
    void franja_respetaLosLimitesDeCadaTramo() {
        assertEquals(FranjaHorariaAgente.MANANA, FranjaHorariaAgente.of(LocalTime.of(0, 0)));
        assertEquals(FranjaHorariaAgente.MANANA, FranjaHorariaAgente.of(LocalTime.of(11, 59)));
        assertEquals(FranjaHorariaAgente.TARDE, FranjaHorariaAgente.of(LocalTime.NOON));
        assertEquals(FranjaHorariaAgente.TARDE, FranjaHorariaAgente.of(LocalTime.of(18, 59)));
        assertEquals(FranjaHorariaAgente.NOCHE, FranjaHorariaAgente.of(LocalTime.of(19, 0)));
        assertEquals(FranjaHorariaAgente.NOCHE, FranjaHorariaAgente.of(LocalTime.of(23, 30)));
    }

    private ListHorarioDisponibleResponse horario(UUID id, LocalDate fecha, LocalTime horaDesde) {
        return new ListHorarioDisponibleResponse(id, UUID.randomUUID(), "Ana", "Gómez", UUID.randomUUID(),
                "Consulta clínica", fecha, horaDesde, horaDesde.plusMinutes(30), ZonedDateTime.now());
    }

}
