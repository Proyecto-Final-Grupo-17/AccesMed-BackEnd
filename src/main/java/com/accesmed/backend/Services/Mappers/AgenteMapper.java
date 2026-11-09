package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.IndicacionPrestacion;
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
import com.accesmed.backend.Records.Agente.Response.GetCotizacionTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetPacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListObraSocialAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListHorarioDisponibleAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListIndicacionPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ObraSocialAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ReprogramTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.UpdatePacienteAgenteResponse;
import com.accesmed.backend.Services.AgenteServices.CotizacionTurnoAgente;
import com.accesmed.backend.Services.Utils.CoincidenciaBusqueda;
import com.accesmed.backend.Services.Utils.FormatoMensaje;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Mapper del canal chatbot. Convierte entidades y responses existentes a los responses de
 * {@code Records/Agente}, que solo exponen claves de negocio (nombres, matrículas, códigos) y
 * nunca ids internos, salvo el {@code slotId} opaco de la disponibilidad.
 */
@Mapper(componentModel = "spring", imports = {FranjaHorariaAgente.class, FormatoMensaje.class})
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
     * Convierte una obra social y sus planes asociables a {@code ListObraSocialAgenteResponse}.
     *
     * @param obraSocial {@code ObraSocial} obra social activa
     * @param planes {@code List<Plan>} sus planes no deshabilitados
     * @return {@code ListObraSocialAgenteResponse} la obra social con los nombres de sus planes
     */
    default ListObraSocialAgenteResponse toListObraSocialResponse(ObraSocial obraSocial, List<Plan> planes) {

        ListObraSocialAgenteResponse listObraSocialAgenteResponse = new ListObraSocialAgenteResponse(
                obraSocial.getNombre(), planes.stream().map(Plan::getNombre).sorted().toList());
        return listObraSocialAgenteResponse;

    }

    /**
     * Convierte una prestación publicada a {@code ListPrestacionAgenteResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param requiereValidacion {@code boolean} si alguna indicación vigente requiere validación
     * @param medicos {@code List<ListMedicoAgenteResponse>} médicos que la atienden, ya mapeados
     * @return {@code ListPrestacionAgenteResponse} prestación con sus médicos
     */
    @Mapping(target = "nombre", source = "prestacion.nombre")
    ListPrestacionAgenteResponse toListPrestacionResponse(Prestacion prestacion, boolean requiereValidacion,
            List<ListMedicoAgenteResponse> medicos);

    /**
     * Convierte una prestación publicada a {@code SearchPrestacionAgenteResponse}.
     *
     * @param prestacion {@code Prestacion} entidad
     * @param requiereValidacion {@code boolean} si alguna indicación vigente requiere validación
     * @param indicaciones {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones vigentes, ya mapeadas
     * @param medicos {@code List<ListMedicoAgenteResponse>} médicos que la atienden, ya mapeados
     * @param coincidencia {@code CoincidenciaBusqueda} cómo se encontró (exacta o aproximada)
     * @return {@code SearchPrestacionAgenteResponse} prestación con su especialidad, indicaciones y médicos
     */
    @Mapping(target = "nombre", source = "prestacion.nombre")
    @Mapping(target = "especialidad", source = "prestacion.especialidad.nombre")
    SearchPrestacionAgenteResponse toSearchPrestacionResponse(Prestacion prestacion, boolean requiereValidacion,
            List<ListIndicacionPrestacionAgenteResponse> indicaciones, List<ListMedicoAgenteResponse> medicos,
            CoincidenciaBusqueda coincidencia);

    /**
     * Convierte un médico a {@code SearchMedicoAgenteResponse}.
     *
     * @param medico {@code Medico} entidad
     * @param prestaciones {@code List<String>} nombres de las prestaciones publicadas que atiende
     * @param coincidencia {@code CoincidenciaBusqueda} cómo se encontró (exacta o aproximada)
     * @return {@code SearchMedicoAgenteResponse} médico con su especialidad y sus prestaciones
     */
    @Mapping(target = "nombre", source = "medico.nombre")
    @Mapping(target = "apellido", source = "medico.apellido")
    @Mapping(target = "matricula", source = "medico.matricula")
    @Mapping(target = "especialidad", source = "medico.especialidad.nombre")
    SearchMedicoAgenteResponse toSearchMedicoResponse(Medico medico, List<String> prestaciones,
            CoincidenciaBusqueda coincidencia);

    /**
     * Convierte una indicación de prestación a {@code ListIndicacionPrestacionAgenteResponse}.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad vigente
     * @return {@code ListIndicacionPrestacionAgenteResponse} indicación con nombre, descripción y si requiere validación
     */
    ListIndicacionPrestacionAgenteResponse toListIndicacionPrestacionResponse(IndicacionPrestacion indicacionPrestacion);

    /**
     * Convierte una lista de indicaciones de prestación a {@code ListIndicacionPrestacionAgenteResponse}.
     *
     * @param indicacionesPrestacion {@code List<IndicacionPrestacion>} entidades vigentes
     * @return {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones mapeadas
     */
    List<ListIndicacionPrestacionAgenteResponse> toListIndicacionPrestacionResponses(
            List<IndicacionPrestacion> indicacionesPrestacion);

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
     * Arma la oferta de una especialidad (prestaciones publicadas con sus médicos).
     *
     * @param especialidad {@code String} nombre de la especialidad
     * @param prestaciones {@code List<ListPrestacionAgenteResponse>} prestaciones ya mapeadas, con sus médicos
     * @return {@code GetOfertaEspecialidadAgenteResponse} oferta de la especialidad
     */
    GetOfertaEspecialidadAgenteResponse toGetOfertaEspecialidadResponse(String especialidad,
            List<ListPrestacionAgenteResponse> prestaciones);

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
    @Mapping(target = "fechaHoraTexto", expression = "java(FormatoMensaje.fechaHoraTexto(toFechaHora(listHorarioDisponibleResponse)))")
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
     * Convierte la fecha y hora de inicio de un turno a la hora local de la clínica. La persistencia
     * devuelve el {@code ZonedDateTime} en UTC y Jackson lo serializa con {@code Z}: el agente
     * mostraba 17:00 para un turno de las 14:00. Va sin zona, igual que la disponibilidad.
     *
     * @param fechaHoraInicio {@code ZonedDateTime} fecha y hora de inicio del turno
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica
     * @return {@code LocalDateTime} fecha y hora de inicio en la zona de la clínica, o {@code null}
     */
    default LocalDateTime toFechaHoraClinica(ZonedDateTime fechaHoraInicio, ZoneId zonaHorariaClinica) {
        return fechaHoraInicio != null ? fechaHoraInicio.withZoneSameInstant(zonaHorariaClinica).toLocalDateTime() : null;
    }

    /**
     * Convierte un turno recién creado a {@code CreateTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad guardada
     * @param estadoActual {@code EstadoTurno} estado inicial del turno
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, para la hora que ve el paciente
     * @param indicaciones {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones del turno, ya mapeadas
     * @return {@code CreateTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "matricula", source = "turno.medico.matricula")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", expression = "java(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica))")
    @Mapping(target = "fechaHoraTexto", expression = "java(FormatoMensaje.fechaHoraTexto(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica)))")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura")
    @Mapping(target = "estadoActual", source = "estadoActual")
    @Mapping(target = "indicaciones", source = "indicaciones")
    CreateTurnoAgenteResponse toCreateTurnoResponse(Turno turno, EstadoTurno estadoActual, ZoneId zonaHorariaClinica,
            List<ListIndicacionPrestacionAgenteResponse> indicaciones);

    /**
     * Convierte el turno nuevo resultante de una reprogramación a {@code ReprogramTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad nueva, con {@code turnoOrigen}
     * @param estadoActual {@code EstadoTurno} estado inicial del turno nuevo
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, para la hora que ve el paciente
     * @return {@code ReprogramTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "matricula", source = "turno.medico.matricula")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", expression = "java(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica))")
    @Mapping(target = "fechaHoraTexto", expression = "java(FormatoMensaje.fechaHoraTexto(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica)))")
    @Mapping(target = "montoAPagar", source = "turno.montoAPagar")
    @Mapping(target = "tipoCobertura", source = "turno.tipoCobertura")
    @Mapping(target = "estadoActual", source = "estadoActual")
    @Mapping(target = "codigoTurnoOrigen", source = "turno.turnoOrigen.codigo")
    ReprogramTurnoAgenteResponse toReprogramTurnoResponse(Turno turno, EstadoTurno estadoActual, ZoneId zonaHorariaClinica);

    /**
     * Convierte un turno confirmado a {@code ConfirmTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad confirmada
     * @param estadoActual {@code EstadoTurno} estado vigente ({@code CONFIRMADO})
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, para la hora que ve el paciente
     * @return {@code ConfirmTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "matricula", source = "turno.medico.matricula")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", expression = "java(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica))")
    @Mapping(target = "fechaHoraTexto", expression = "java(FormatoMensaje.fechaHoraTexto(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica)))")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ConfirmTurnoAgenteResponse toConfirmTurnoResponse(Turno turno, EstadoTurno estadoActual, ZoneId zonaHorariaClinica);

    /**
     * Convierte un turno del paciente a {@code ListTurnoAgenteResponse}.
     *
     * @param turno {@code Turno} entidad
     * @param estadoActual {@code EstadoTurno} estado vigente del turno
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, para la hora que ve el paciente
     * @return {@code ListTurnoAgenteResponse} turno sin ids internos
     */
    @Mapping(target = "nombrePrestacion", source = "turno.prestacion.nombre")
    @Mapping(target = "nombreMedico", source = "turno.medico.nombre")
    @Mapping(target = "apellidoMedico", source = "turno.medico.apellido")
    @Mapping(target = "matricula", source = "turno.medico.matricula")
    @Mapping(target = "codigo", source = "turno.codigo")
    @Mapping(target = "fechaHoraInicio", expression = "java(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica))")
    @Mapping(target = "fechaHoraTexto", expression = "java(FormatoMensaje.fechaHoraTexto(toFechaHoraClinica(turno.getFechaHoraInicio(), zonaHorariaClinica)))")
    @Mapping(target = "estadoActual", source = "estadoActual")
    ListTurnoAgenteResponse toListTurnoResponse(Turno turno, EstadoTurno estadoActual, ZoneId zonaHorariaClinica);

    /**
     * Arma la cotización de un turno ({@code GetCotizacionTurnoAgenteResponse}) a partir del slot ya
     * resuelto y del resultado de {@code CotizacionAgenteService}.
     *
     * @param medico {@code Medico} médico del horario
     * @param prestacion {@code Prestacion} prestación del horario
     * @param fechaHoraInicio {@code ZonedDateTime} inicio del turno que se reservaría
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, para la hora que ve el paciente
     * @param cotizacionTurnoAgente {@code CotizacionTurnoAgente} cobertura y monto calculados
     * @param indicaciones {@code List<ListIndicacionPrestacionAgenteResponse>} indicaciones vigentes, ya mapeadas
     * @return {@code GetCotizacionTurnoAgenteResponse} la cotización sin ids internos
     */
    default GetCotizacionTurnoAgenteResponse toGetCotizacionTurnoResponse(Medico medico, Prestacion prestacion,
            ZonedDateTime fechaHoraInicio, ZoneId zonaHorariaClinica, CotizacionTurnoAgente cotizacionTurnoAgente,
            List<ListIndicacionPrestacionAgenteResponse> indicaciones) {

        LocalDateTime fechaHoraInicioClinica = toFechaHoraClinica(fechaHoraInicio, zonaHorariaClinica);
        ObraSocialPaciente coberturaQueCubre = cotizacionTurnoAgente.obraSocialPaciente();

        GetCotizacionTurnoAgenteResponse getCotizacionTurnoAgenteResponse = new GetCotizacionTurnoAgenteResponse(
                fechaHoraInicioClinica,
                FormatoMensaje.fechaHoraTexto(fechaHoraInicioClinica),
                prestacion.getNombre(),
                medico.getNombre(),
                medico.getApellido(),
                medico.getMatricula(),
                cotizacionTurnoAgente.tipoCobertura(),
                coberturaQueCubre != null ? coberturaQueCubre.getPlan().getObraSocial().getNombre() : null,
                coberturaQueCubre != null ? coberturaQueCubre.getPlan().getNombre() : null,
                cotizacionTurnoAgente.cubre(),
                cotizacionTurnoAgente.motivo(),
                cotizacionTurnoAgente.montoAPagar(),
                cotizacionTurnoAgente.precioParticular(),
                indicaciones.stream().anyMatch(ListIndicacionPrestacionAgenteResponse::requiereValidacion),
                indicaciones);
        return getCotizacionTurnoAgenteResponse;

    }

    //endregion

}
