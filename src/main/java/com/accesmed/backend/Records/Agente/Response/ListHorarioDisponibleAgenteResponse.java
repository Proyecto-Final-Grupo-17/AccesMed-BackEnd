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
 * @param fechaHoraTexto {@code String} la misma fecha y hora legible, con el día de la semana ({@code jueves 12/11/2026 09:30}); el agente la copia tal cual
 * @param franja {@code FranjaHorariaAgente} franja del día
 */
public record ListHorarioDisponibleAgenteResponse(
        UUID slotId,
        LocalDateTime fechaHora,
        String fechaHoraTexto,
        FranjaHorariaAgente franja
) {
}
