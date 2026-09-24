package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Records.AgendaMedico.Request.CreateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Response.CreateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.DiaAgendaResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.GetAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.HorarioAgendaResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaHorarioResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Mapper para el agregado de Agenda ({@code AgendaMedico}/{@code AgendaHorariosDia}). Las
 * conversiones simples (entidad plana → response plano) las resuelve MapStruct; las
 * respuestas compuestas (agenda con días y horarios expandidos) se arman con métodos
 * {@code default}, agrupando la lista plana de {@code AgendaHorariosDia} activos por
 * {@code fecha} (el día ya no es una entidad propia).
 */
@Mapper(componentModel = "spring")
public interface AgendaMedicoMapper {

    /**
     * Convierte un {@code CreateAgendaMedicoRequest} a una entidad {@code AgendaMedico}.
     *
     * @param createAgendaMedicoRequest {@code CreateAgendaMedicoRequest} datos del request
     * @return {@code AgendaMedico} entidad lista para persistir (sin médico)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medico", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    AgendaMedico toEntity(CreateAgendaMedicoRequest createAgendaMedicoRequest);

    /**
     * Convierte una entidad {@code AgendaHorariosDia} a {@code HorarioAgendaResponse}.
     *
     * @param agendaHorariosDia {@code AgendaHorariosDia} entidad
     * @return {@code HorarioAgendaResponse} respuesta de horario anidado
     */
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    HorarioAgendaResponse toHorarioAgendaResponse(AgendaHorariosDia agendaHorariosDia);

    /**
     * Convierte una lista de entidades {@code AgendaHorariosDia} a una lista de
     * {@code HorarioAgendaResponse}.
     *
     * @param agendaHorariosDia {@code List<AgendaHorariosDia>} lista de entidades
     * @return {@code List<HorarioAgendaResponse>} lista de respuestas
     */
    List<HorarioAgendaResponse> toHorarioAgendaResponses(List<AgendaHorariosDia> agendaHorariosDia);

    /**
     * Convierte una entidad {@code AgendaHorariosDia} a {@code ListAgendaHorarioResponse}.
     *
     * @param agendaHorariosDia {@code AgendaHorariosDia} entidad
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListAgendaHorarioResponse} fila del listado de {@code listHorariosAgenda}
     */
    @Mapping(target = "medicoId", source = "agendaHorariosDia.agendaMedico.medico.id")
    @Mapping(target = "prestacionId", source = "agendaHorariosDia.prestacion.id")
    @Mapping(target = "prestacionNombre", source = "agendaHorariosDia.prestacion.nombre")
    @Mapping(target = "auditoria", source = "auditoria")
    ListAgendaHorarioResponse toListHorarioResponse(AgendaHorariosDia agendaHorariosDia, AuditoriaResponse auditoria);

    /**
     * Convierte una lista de entidades {@code AgendaHorariosDia} a una lista de
     * {@code ListAgendaHorarioResponse}.
     *
     * @param agendaHorariosDia {@code List<AgendaHorariosDia>} lista de entidades
     * @return {@code List<ListAgendaHorarioResponse>} lista de respuestas
     */
    List<ListAgendaHorarioResponse> toListHorarioResponses(List<AgendaHorariosDia> agendaHorariosDia);

    /**
     * Convierte una entidad {@code AgendaHorariosDia} a {@code ListHorarioDisponibleResponse}.
     *
     * @param agendaHorariosDia {@code AgendaHorariosDia} entidad
     * @return {@code ListHorarioDisponibleResponse} fila del listado de {@code listHorariosDisponibles}
     */
    @Mapping(target = "medicoId", source = "agendaMedico.medico.id")
    @Mapping(target = "medicoNombre", source = "agendaMedico.medico.nombre")
    @Mapping(target = "medicoApellido", source = "agendaMedico.medico.apellido")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    ListHorarioDisponibleResponse toListHorarioDisponibleResponse(AgendaHorariosDia agendaHorariosDia);

    /**
     * Convierte una lista de entidades {@code AgendaHorariosDia} a una lista de
     * {@code ListHorarioDisponibleResponse}.
     *
     * @param agendaHorariosDia {@code List<AgendaHorariosDia>} lista de entidades
     * @return {@code List<ListHorarioDisponibleResponse>} lista de respuestas
     */
    List<ListHorarioDisponibleResponse> toListHorarioDisponibleResponses(List<AgendaHorariosDia> agendaHorariosDia);

    /**
     * Arma la lista de {@code DiaAgendaResponse} de una agenda, agrupando los horarios
     * activos por {@code fecha} (orden cronológico).
     *
     * @param horarios {@code List<AgendaHorariosDia>} horarios activos de la agenda
     * @return {@code List<DiaAgendaResponse>} días con sus horarios expandidos
     */
    default List<DiaAgendaResponse> toDiaAgendaResponses(List<AgendaHorariosDia> horarios) {

        Map<LocalDate, List<AgendaHorariosDia>> horariosPorFecha = horarios.stream()
                .collect(Collectors.groupingBy(AgendaHorariosDia::getFecha, TreeMap::new, Collectors.toList()));

        return horariosPorFecha.entrySet().stream()
                .map(entrada -> new DiaAgendaResponse(entrada.getKey(), toHorarioAgendaResponses(entrada.getValue())))
                .toList();

    }

    /**
     * Arma el {@code CreateAgendaMedicoResponse} a partir de la agenda guardada y sus días
     * ya expandidos.
     *
     * @param agendaMedico {@code AgendaMedico} agenda guardada
     * @param dias {@code List<DiaAgendaResponse>} días generados, con sus horarios
     * @param cantidadHorariosGenerados {@code int} cantidad total de slots generados
     * @return {@code CreateAgendaMedicoResponse} respuesta del alta
     */
    default CreateAgendaMedicoResponse toCreateResponse(AgendaMedico agendaMedico, List<DiaAgendaResponse> dias, int cantidadHorariosGenerados) {

        return new CreateAgendaMedicoResponse(agendaMedico.getId(), agendaMedico.getMedico().getId(),
                agendaMedico.getFechaInicioVigencia(), agendaMedico.getFechaFinVigencia(), dias, cantidadHorariosGenerados);

    }

    /**
     * Arma el {@code GetAgendaMedicoResponse} a partir de la agenda, sus días activos ya
     * expandidos y su información de auditoría.
     *
     * @param agendaMedico {@code AgendaMedico} agenda encontrada
     * @param dias {@code List<DiaAgendaResponse>} días activos, con sus horarios activos
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code GetAgendaMedicoResponse} respuesta de la búsqueda puntual
     */
    default GetAgendaMedicoResponse toGetResponse(AgendaMedico agendaMedico, List<DiaAgendaResponse> dias, AuditoriaResponse auditoria) {

        return new GetAgendaMedicoResponse(agendaMedico.getId(), agendaMedico.getMedico().getId(),
                agendaMedico.getMedico().getNombre(), agendaMedico.getMedico().getApellido(),
                agendaMedico.getFechaInicioVigencia(), agendaMedico.getFechaFinVigencia(), dias, auditoria);

    }

    /**
     * Arma una fila de {@code listAgendaMedico} a partir de la agenda, sus conteos ya
     * resueltos por el {@code App} y su información de auditoría.
     *
     * @param agendaMedico {@code AgendaMedico} agenda
     * @param cantidadDias {@code long} cantidad de días activos de la agenda
     * @param cantidadHorarios {@code long} cantidad de horarios activos de la agenda
     * @param auditoria {@code AuditoriaResponse} datos de auditoría, o {@code null} si quien
     *         consulta no tiene {@code AUDITORIA_CONSULTAR}
     * @return {@code ListAgendaMedicoResponse} fila del listado
     */
    default ListAgendaMedicoResponse toListResponse(AgendaMedico agendaMedico, long cantidadDias, long cantidadHorarios, AuditoriaResponse auditoria) {

        return new ListAgendaMedicoResponse(agendaMedico.getId(), agendaMedico.getMedico().getId(),
                agendaMedico.getMedico().getNombre(), agendaMedico.getMedico().getApellido(),
                agendaMedico.getFechaInicioVigencia(), agendaMedico.getFechaFinVigencia(),
                cantidadDias, cantidadHorarios, auditoria);

    }

    /**
     * Arma el {@code AuditoriaResponse} de un horario de agenda ({@code AgendaHorariosDia}).
     * {@code AgendaHorariosDia} tiene soft delete propio ({@code deletedAt}/{@code deletedBy}/
     * {@code deletedReason}), así que los mapeos son directos.
     *
     * @param agendaHorariosDia {@code AgendaHorariosDia} entidad
     * @return {@code AuditoriaResponse} datos de auditoría del horario
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "deletedBy", source = "deletedBy")
    @Mapping(target = "deletedReason", source = "deletedReason")
    AuditoriaResponse toAuditoria(AgendaHorariosDia agendaHorariosDia);

    /**
     * Arma el {@code AuditoriaResponse} de una agenda ({@code AgendaMedico}). {@code AgendaMedico}
     * no tiene soft delete propio (se gestiona por vigencia), así que {@code deletedAt}/{@code deletedBy}/
     * {@code deletedReason} siempre viajan en {@code null}.
     *
     * @param agendaMedico {@code AgendaMedico} entidad
     * @return {@code AuditoriaResponse} datos de auditoría de la agenda
     */
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    AuditoriaResponse toAuditoria(AgendaMedico agendaMedico);

}
