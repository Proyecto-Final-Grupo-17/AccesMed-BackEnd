package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.AgendaDia;
import com.accesmed.backend.Domain.AgendaHorarios;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mapper para el agregado de Agenda ({@code AgendaMedico}/{@code AgendaDia}/
 * {@code AgendaHorarios}). Las conversiones simples (entidad plana → response plano) las
 * resuelve MapStruct; las respuestas compuestas (agenda con días y horarios expandidos)
 * se arman con métodos {@code default}, porque {@code AgendaMedico} no navega a sus días
 * (relación unidireccional, solo el {@code @ManyToOne} de {@code AgendaDia} la mapea) y el
 * conjunto de días/horarios activos a incluir lo resuelve el {@code App} con los
 * {@code DomainService} correspondientes.
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
     * Convierte una entidad {@code AgendaHorarios} a {@code HorarioAgendaResponse}.
     *
     * @param agendaHorarios {@code AgendaHorarios} entidad
     * @return {@code HorarioAgendaResponse} respuesta de horario anidado
     */
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    HorarioAgendaResponse toHorarioAgendaResponse(AgendaHorarios agendaHorarios);

    /**
     * Convierte una lista de entidades {@code AgendaHorarios} a una lista de
     * {@code HorarioAgendaResponse}.
     *
     * @param agendaHorarios {@code List<AgendaHorarios>} lista de entidades
     * @return {@code List<HorarioAgendaResponse>} lista de respuestas
     */
    List<HorarioAgendaResponse> toHorarioAgendaResponses(List<AgendaHorarios> agendaHorarios);

    /**
     * Convierte una entidad {@code AgendaHorarios} a {@code ListAgendaHorarioResponse},
     * navegando hasta el médico y la fecha del día a través de {@code agendaDia}.
     *
     * @param agendaHorarios {@code AgendaHorarios} entidad
     * @return {@code ListAgendaHorarioResponse} fila del listado de {@code listHorariosAgenda}
     */
    @Mapping(target = "agendaDiaId", source = "agendaDia.id")
    @Mapping(target = "fecha", source = "agendaDia.fecha")
    @Mapping(target = "medicoId", source = "agendaDia.agendaMedico.medico.id")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    ListAgendaHorarioResponse toListHorarioResponse(AgendaHorarios agendaHorarios);

    /**
     * Convierte una lista de entidades {@code AgendaHorarios} a una lista de
     * {@code ListAgendaHorarioResponse}.
     *
     * @param agendaHorarios {@code List<AgendaHorarios>} lista de entidades
     * @return {@code List<ListAgendaHorarioResponse>} lista de respuestas
     */
    List<ListAgendaHorarioResponse> toListHorarioResponses(List<AgendaHorarios> agendaHorarios);

    /**
     * Convierte una entidad {@code AgendaHorarios} a {@code ListHorarioDisponibleResponse},
     * navegando hasta el médico y la fecha del día a través de {@code agendaDia}.
     *
     * @param agendaHorarios {@code AgendaHorarios} entidad
     * @return {@code ListHorarioDisponibleResponse} fila del listado de {@code listHorariosDisponibles}
     */
    @Mapping(target = "medicoId", source = "agendaDia.agendaMedico.medico.id")
    @Mapping(target = "medicoNombre", source = "agendaDia.agendaMedico.medico.nombre")
    @Mapping(target = "medicoApellido", source = "agendaDia.agendaMedico.medico.apellido")
    @Mapping(target = "prestacionId", source = "prestacion.id")
    @Mapping(target = "prestacionNombre", source = "prestacion.nombre")
    @Mapping(target = "fecha", source = "agendaDia.fecha")
    ListHorarioDisponibleResponse toListHorarioDisponibleResponse(AgendaHorarios agendaHorarios);

    /**
     * Convierte una lista de entidades {@code AgendaHorarios} a una lista de
     * {@code ListHorarioDisponibleResponse}.
     *
     * @param agendaHorarios {@code List<AgendaHorarios>} lista de entidades
     * @return {@code List<ListHorarioDisponibleResponse>} lista de respuestas
     */
    List<ListHorarioDisponibleResponse> toListHorarioDisponibleResponses(List<AgendaHorarios> agendaHorarios);

    /**
     * Arma un {@code DiaAgendaResponse} a partir de un día y sus horarios activos.
     *
     * @param agendaDia {@code AgendaDia} día
     * @param horarios {@code List<AgendaHorarios>} horarios activos de ese día
     * @return {@code DiaAgendaResponse} día con sus horarios expandidos
     */
    default DiaAgendaResponse toDiaAgendaResponse(AgendaDia agendaDia, List<AgendaHorarios> horarios) {

        return new DiaAgendaResponse(agendaDia.getId(), agendaDia.getFecha(), toHorarioAgendaResponses(horarios));

    }

    /**
     * Arma la lista de {@code DiaAgendaResponse} de una agenda, indexando los horarios por
     * día a partir del mapa provisto por el {@code App} (evita N+1).
     *
     * @param dias {@code List<AgendaDia>} días activos de la agenda
     * @param horariosPorDia {@code Map<UUID, List<AgendaHorarios>>} horarios activos, indexados por id de día
     * @return {@code List<DiaAgendaResponse>} días con sus horarios expandidos
     */
    default List<DiaAgendaResponse> toDiaAgendaResponses(List<AgendaDia> dias, Map<UUID, List<AgendaHorarios>> horariosPorDia) {

        return dias.stream()
                .map(dia -> toDiaAgendaResponse(dia, horariosPorDia.getOrDefault(dia.getId(), List.of())))
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
