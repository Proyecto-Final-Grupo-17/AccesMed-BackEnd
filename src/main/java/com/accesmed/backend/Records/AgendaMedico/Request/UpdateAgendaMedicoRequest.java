package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Delta de composición sobre los slots de una agenda ({@code PATCH /Agenda/{id}}). Todos
 * los campos de la colección son opcionales; {@code null} o ausente significa "sin cambios
 * en ese frente". Ver el cuarto caso de actualización en {@code Docs/ARQUITECTURA.md §5.2}.
 *
 * @param id {@code UUID} identificador de la agenda (validado contra la ruta en el Controller)
 * @param horariosAAgregar {@code List<HorarioAAgregarRequest>} bloques horarios nuevos a agregar
 * @param horariosAExcluir {@code List<UUID>} identificadores de {@code AgendaHorarios} a dar de baja
 * @param diasAExcluir {@code List<UUID>} identificadores de {@code AgendaDia} a dar de baja
 *        completos (atajo del feriado: da de baja el día y todos sus horarios)
 */
public record UpdateAgendaMedicoRequest(
        @NotNull UUID id,
        List<@Valid HorarioAAgregarRequest> horariosAAgregar,
        List<UUID> horariosAExcluir,
        List<UUID> diasAExcluir
) {

}
