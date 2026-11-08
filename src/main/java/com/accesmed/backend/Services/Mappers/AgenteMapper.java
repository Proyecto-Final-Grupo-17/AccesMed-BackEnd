package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Response.ConfirmTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreatePacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreateTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetClinicaAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetOfertaEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetPacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListHorarioDisponibleAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ObraSocialAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ReprogramTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.UpdatePacienteAgenteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper del canal chatbot. Convierte entidades y responses existentes a los responses de
 * {@code Records/Agente}, que solo exponen claves de negocio (nombres, matrículas, códigos) y
 * nunca ids internos, salvo el {@code slotId} opaco de la disponibilidad.
 */
@Mapper(componentModel = "spring", imports = FranjaHorariaAgente.class)
public interface AgenteMapper {

    //region ========== Paciente ==========

    /**
     * Convierte una cobertura de obra social a {@code ObraSocialAgenteResponse}.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} cobertura del paciente
     * @return {@code ObraSocialAgenteResponse} cobertura con nombres de obra social y plan
     */
    @Mapping(target = "obraSocialNombre", source = "plan.obraSocial.nombre")
    @Mapping(target = "planNombre", source = "plan.nombre")
    ObraSocialAgenteResponse toObraSocialResponse(ObraSocialPaciente obraSocialPaciente);

    /**
     * Convierte una lista de coberturas a {@code ObraSocialAgenteResponse}.
     *
     * @param obrasSocialesPaciente {@code List<ObraSocialPaciente>} coberturas del paciente
     * @return {@code List<ObraSocialAgenteResponse>} coberturas mapeadas
     */
    List<ObraSocialAgenteResponse> toObraSocialResponses(List<ObraSocialPaciente> obrasSocialesPaciente);

    /**
     * Convierte un paciente y sus coberturas a {@code GetPacienteAgenteResponse}.
     *
     * @param paciente {@code Paciente} entidad
     * @param obrasSociales {@code List<ObraSocialAgenteResponse>} coberturas ya mapeadas
     * @return {@code GetPacienteAgenteResponse} datos del paciente sin ids
     */
    GetPacienteAgenteResponse toGetPacienteResponse(Paciente paciente, List<ObraSocialAgenteResponse> obrasSociales);

    /**
     * Convierte un paciente recién registrado a {@code CreatePacienteAgenteResponse}.
     *
     * @param paciente {@code Paciente} entidad guardada
     * @return {@code CreatePacienteAgenteResponse} confirmación del registro
     */
    CreatePacienteAgenteResponse toCreatePacienteResponse(Paciente paciente);

    /**
     * Convierte un paciente actualizado y sus coberturas a {@code UpdatePacienteAgenteResponse}.
     *
     * @param paciente {@code Paciente} entidad actualizada
     * @param obrasSociales {@code List<ObraSocialAgenteResponse>} coberturas ya mapeadas
     * @return {@code UpdatePacienteAgenteResponse} datos del paciente sin ids
     */
    UpdatePacienteAgenteResponse toUpdatePacienteResponse(Paciente paciente, List<ObraSocialAgenteResponse> obrasSociales);

    //endregion

    //region ========== Información general ==========

    /**
     * Convierte la clínica a {@code GetClinicaAgenteResponse}.
     *
     * @param clinica {@code Clinica} entidad
     * @return {@code GetClinicaAgenteResponse} información general de la clínica
     */
    GetClinicaAgenteResponse toGetClinicaResponse(Clinica clinica);

    /**
     * Convierte una especialidad a {@code ListEspecialidadAgenteResponse}.
     *
     * @param especialidad {@code Especialidad} entidad
     * @return {@code ListEspecialidadAgenteResponse} especialidad con su nombre
     */
    ListEspecialidadAgenteResponse toListEspecialidadResponse(Especialidad especialidad);

    /**
     * Convierte una lista de especialidades a {@code ListEspecialidadAgenteResponse}.
     *
     * @param especialidades {@code List<Especialidad>} entidades
     * @return {@code List<ListEspecialidadAgenteResponse>} especialidades mapeadas
     */
    List<ListEspecialidadAgenteResponse> toListEspecialidadResponses(List<Especialidad> especialidades);

    /**
     * Convierte una prestación a {@code ListPrestacionAgenteResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @return {@code ListPrestacionAgenteResponse} prestación con su nombre
     */
    ListPrestacionAgenteResponse toListPrestacionResponse(Prestacion prestacion);

    /**
     * Convierte una lista de prestaciones a {@code ListPrestacionAgenteResponse}.
     *
     * @param prestaciones {@code List<Prestacion>} entidades
     * @return {@code List<ListPrestacionAgenteResponse>} prestaciones mapeadas
     */
    List<ListPrestacionAgenteResponse> toListPrestacionResponses(List<Prestacion> prestaciones);

    /**
     * Convierte un médico a {@code ListMedicoAgenteResponse}.
     *
     * @param medico {@code Medico} entidad
     * @return {@code ListMedicoAgenteResponse} médico con nombre, apellido y matrícula
     */
    ListMedicoAgenteResponse toListMedicoResponse(Medico medico);

    /**
     * Convierte una lista de médicos a {@code ListMedicoAgenteResponse}.
     *
     * @param medicos {@code List<Medico>} entidades
     * @return {@code List<ListMedicoAgenteResponse>} médicos mapeados
     */
    List<ListMedicoAgenteResponse> toListMedicoResponses(List<Medico> medicos);

    /**
     * Arma la oferta de una especialidad (prestaciones publicadas y médicos activos).
     *
     * @param especialidad {@code String} nombre de la especialidad
     * @param prestaciones {@code List<ListPrestacionAgenteResponse>} prestaciones ya mapeadas
     * @param medicos {@code List<ListMedicoAgenteResponse>} médicos ya mapeados
     * @return {@code GetOfertaEspecialidadAgenteResponse} oferta de la especialidad
     */
    GetOfertaEspecialidadAgenteResponse toGetOfertaEspecialidadResponse(String especialidad,
            List<ListPrestacionAgenteResponse> prestaciones, List<ListMedicoAgenteResponse> medicos);

    //endregion

    //region ========== Turnos ==========

    /**
     * Convierte un horario disponible a {@code ListHorarioDisponibleAgenteResponse}.
     *
     * @param listHorarioDisponibleResponse {@code ListHorarioDisponibleResponse} horario del listado de disponibilidad
     * @return {@code ListHorarioDisponibleAgenteResponse} horario con su {@code slotId}, fecha-hora y franja
     */
    @Mapping(target = "slotId", source = "id")
    @Mapping(target = "fechaHora", expression = "java(toFechaHora(listHorarioDisponibleResponse))")
    @Mapping(target = "franja", expression = "java(FranjaHorariaAgente.of(listHorarioDisponibleResponse.horaDesde()))")
    ListHorarioDisponibleAgenteResponse toListHorarioDisponibleResponse(ListHorarioDisponibleResponse listHorarioDisponibleResponse);

    /**
     * Combina fecha y hora de inicio de un horario en un único {@code LocalDateTime}.
     *
     * @param listHorarioDisponibleResponse {@code ListHorarioDisponibleResponse} horario del listado
     * @return {@code LocalDateTime} fecha y hora de inicio
     */
    default LocalDateTime toFechaHora(ListHorarioDisponibleResponse listHorarioDisponibleResponse) {
        return listHorarioDisponibleResponse.fecha().atTime(listHorarioDisponibleResponse.horaDesde());
    }

    /**
     * Convierte un turno recién creado a {@code CreateTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad guardada
     * @param estadoActual {@code EstadoTurno} estado inicial del turno
     * @return {@code CreateTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura")
    @Mapping(target = "estadoActual", source = "estadoActual")
    CreateTurnoAgenteResponse toCreateTurnoResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte el turno nuevo resultante de una reprogramación a {@code ReprogramTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad nueva, con {@code turnoOrigen}
     * @param estadoActual {@code EstadoTurno} estado inicial del turno nuevo
     * @return {@code ReprogramTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura")
    @Mapping(target = "estadoActual", source = "estadoActual")
    @Mapping(target = "codigoTurnoOrigen", source = "turno.turnoOrigen.codigo")
    ReprogramTurnoAgenteResponse toReprogramTurnoResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte un turno confirmado a {@code ConfirmTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad confirmada
     * @param estadoActual {@code EstadoTurno} estado vigente ({@code CONFIRMADO})
     * @return {@code ConfirmTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ConfirmTurnoAgenteResponse toConfirmTurnoResponse(Turno turno, EstadoTurno estadoActual);

    /**
     * Convierte un turno del paciente a {@code ListTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad
     * @param estadoActual {@code EstadoTurno} estado vigente del turno
     * @return {@code ListTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", source = "turno.fechaHoraInicio")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ListTurnoAgenteResponse toListTurnoResponse(Turno turno, EstadoTurno estadoActual);

    //endregion

}
