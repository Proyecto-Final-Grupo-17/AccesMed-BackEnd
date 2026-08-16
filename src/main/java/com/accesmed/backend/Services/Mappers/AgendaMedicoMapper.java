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
     * @return {@code ListAgendaHorarioResponse} fila del listado de {@code listHorariosAgenda}
     */
    @Mapping(target = "medicoId", source = "agendaMedico.medico.id")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    ListAgendaHorarioResponse toListHorarioResponse(AgendaHorariosDia agendaHorariosDia);

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
                agendaMedico.getFechaHoraInicioVigencia(), agendaMedico.getFechaHoraFinVigencia(), dias, cantidadHorariosGenerados);

    }

    /**
     * Arma el {@code GetAgendaMedicoResponse} a partir de la agenda y sus días activos ya
     * expandidos.
     *
     * @param agendaMedico {@code AgendaMedico} agenda encontrada
     * @param dias {@code List<DiaAgendaResponse>} días activos, con sus horarios activos
     * @return {@code GetAgendaMedicoResponse} respuesta de la búsqueda puntual
     */
    default GetAgendaMedicoResponse toGetResponse(AgendaMedico agendaMedico, List<DiaAgendaResponse> dias) {

        return new GetAgendaMedicoResponse(agendaMedico.getId(), agendaMedico.getMedico().getId(),
                agendaMedico.getMedico().getNombre(), agendaMedico.getMedico().getApellido(),
                agendaMedico.getFechaHoraInicioVigencia(), agendaMedico.getFechaHoraFinVigencia(), dias);

    }

    /**
     * Arma una fila de {@code listAgendaMedico} a partir de la agenda y sus conteos ya
     * resueltos por el {@code App}.
     *
     * @param agendaMedico {@code AgendaMedico} agenda
     * @param cantidadDias {@code long} cantidad de días activos de la agenda
     * @param cantidadHorarios {@code long} cantidad de horarios activos de la agenda
     * @return {@code ListAgendaMedicoResponse} fila del listado
     */
    default ListAgendaMedicoResponse toListResponse(AgendaMedico agendaMedico, long cantidadDias, long cantidadHorarios) {

        return new ListAgendaMedicoResponse(agendaMedico.getId(), agendaMedico.getMedico().getId(),
                agendaMedico.getMedico().getNombre(), agendaMedico.getMedico().getApellido(),
                agendaMedico.getFechaHoraInicioVigencia(), agendaMedico.getFechaHoraFinVigencia(),
                cantidadDias, cantidadHorarios);

    }

}
