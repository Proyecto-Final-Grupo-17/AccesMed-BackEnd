package com.accesmed.backend.Records.AgendaMedico.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code getAgendaMedico}: la agenda con sus días y horarios activos
 * expandidos. Reemplazo de {@code GET /{id}} según {@code Docs/FILTRADO-DINAMICO.md §3}:
 * se resuelve con {@code findOneByCriteria} sobre {@code AgendaMedicoCriteria}.
 *
 * @param id {@code UUID} identificador de la agenda
 * @param medicoId {@code UUID} identificador del médico dueño de la agenda
 * @param medicoNombre {@code String} nombre del médico dueño de la agenda
 * @param medicoApellido {@code String} apellido del médico dueño de la agenda
 * @param fechaHoraInicioVigencia {@code ZonedDateTime} inicio del período de vigencia
 * @param fechaHoraFinVigencia {@code ZonedDateTime} fin del período de vigencia
 * @param dias {@code List<DiaAgendaResponse>} días activos, con sus horarios activos
 * @param auditoria {@code AuditoriaResponse} datos de auditoría, solo poblado si quien consulta
 *         tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario
 */
public record GetAgendaMedicoResponse(
        UUID id,
        UUID medicoId,
        String medicoNombre,
        String medicoApellido,
        ZonedDateTime fechaHoraInicioVigencia,
        ZonedDateTime fechaHoraFinVigencia,
        List<DiaAgendaResponse> dias,
        AuditoriaResponse auditoria
) {

}
