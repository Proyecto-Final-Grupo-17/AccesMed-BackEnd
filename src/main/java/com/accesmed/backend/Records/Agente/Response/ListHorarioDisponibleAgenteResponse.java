package com.accesmed.backend.Records.Agente.Response;

import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Horario disponible para reservar. El {@code slotId} es un identificador opaco: el agente lo
 * conserva para solicitar o reprogramar y solo le muestra al paciente la {@code fechaHora}.
 *
 * @param slotId {@code UUID} identificador opaco del horario
 * @param fechaHora {@code LocalDateTime} fecha y hora de inicio del horario
 * @param franja {@code FranjaHorariaAgente} franja del día
 */
public record ListHorarioDisponibleAgenteResponse(
        UUID slotId,
        LocalDateTime fechaHora,
        FranjaHorariaAgente franja
) {
}
