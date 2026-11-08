package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MotivoCancelacion;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoCobertura;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Notifications.TipoNotificacionTurno;
import com.accesmed.backend.Notifications.TurnoNotificacionEvent;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.Agente.Request.AccionTurnoAgente;
import com.accesmed.backend.Records.Agente.Request.CreatePacienteAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.CreateTurnoAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Request.ObraSocialAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.ReprogramTurnoAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.UpdatePacienteAgenteRequest;
import com.accesmed.backend.Records.Agente.Response.ConfirmTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreatePacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreateTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetClinicaAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetOfertaEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetPacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListHorarioDisponibleAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListObraSocialAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ObraSocialAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ReprogramTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.UpdatePacienteAgenteResponse;
import com.accesmed.backend.Records.Paciente.Request.CreatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Request.UpdatePacienteRequest;
import com.accesmed.backend.Services.DomainServices.AgendaHorariosDiaDomainService;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPlanDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.AgenteMapper;
import com.accesmed.backend.Services.Mappers.PacienteMapper;
import com.accesmed.backend.Services.QueryServices.AgendaHorariosDiaQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.LocalTimeFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import com.accesmed.backend.Services.Utils.ContextoCalculoMontoTurno;
import com.accesmed.backend.Services.Utils.EstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.FabricaEstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.NormalizadorTelefono;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso del canal chatbot (agente de WhatsApp, actor Paciente). Cada método resuelve al
 * paciente por el teléfono verificado del canal y las claves de negocio (nombres, matrículas,
 * códigos) a entidades, aplica el scope por paciente y ejecuta la misma orquestación que el
 * panel web. Esa orquestación está <b>copiada</b> de {@code PacienteApp} y {@code TurnoApp} a
 * propósito: ningún {@code App} llama a otro, y así el panel queda intacto. Solo usa
 * DomainServices, QueryServices, Mappers y utilidades.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgenteApp {

    //region ========== Dependencias o inyecciones ==========

    //Domain Services
    private final PacienteDomainService pacienteDomainService;
    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanDomainService planDomainService;
    private final TurnoDomainService turnoDomainService;
    private final HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    private final AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final HistoricoEstadoPlanDomainService historicoEstadoPlanDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final IndicacionPrestacionTurnoDomainService indicacionPrestacionTurnoDomainService;
    private final ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    private final EspecialidadDomainService especialidadDomainService;
    private final ClinicaDomainService clinicaDomainService;

    //Query Services
    private final AgendaHorariosDiaQueryService agendaHorariosDiaQueryService;

    //Utils y mappers
    private final FabricaEstrategiaCalcularMontoAPagarTurno fabricaEstrategiaCalcularMontoAPagarTurno;
    private final AgenteMapper agenteMapper;
    private final PacienteMapper pacienteMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    //endregion

    //region ========== Paciente ==========

    /**
     * Obtiene los datos del paciente identificado por el teléfono del canal, con sus coberturas
     * activas. Sirve al agente para saber si el número ya está registrado.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @return {@code GetPacienteAgenteResponse} datos del paciente sin ids internos
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no hay un
     *         paciente activo con ese teléfono
     */
    @Transactional(readOnly = true)
    public GetPacienteAgenteResponse getPaciente(String numeroTelefono) {

        log.info("Consulta de paciente por canal iniciada");

        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);

        List<ObraSocialAgenteResponse> obrasSocialesResponse = agenteMapper.toObraSocialResponses(
                obraSocialPacienteDomainService.findCoberturasActivasByPaciente(pacienteExistente.getId()));

        GetPacienteAgenteResponse getPacienteAgenteResponse =
                agenteMapper.toGetPacienteResponse(pacienteExistente, obrasSocialesResponse);
        return getPacienteAgenteResponse;

    }

    /**
     * Registra un paciente nuevo con el teléfono verificado del canal y, si la declara, su
     * cobertura de obra social (copia de la orquestación de {@code PacienteApp.createPaciente}).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param createPacienteAgenteRequest {@code CreatePacienteAgenteRequest} datos del paciente
     * @return {@code CreatePacienteAgenteResponse} confirmación con nombre y apellido
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social
     *         o el plan indicados no existen
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el DNI, el teléfono o el
     *         email ya están registrados
     */
    @Transactional
    public CreatePacienteAgenteResponse createPaciente(String numeroTelefono,
            CreatePacienteAgenteRequest createPacienteAgenteRequest) {

        log.info("Registro de paciente por canal iniciado: dni={}", createPacienteAgenteRequest.dni());

        //Validar que el DNI, el teléfono y el email sean únicos
        pacienteDomainService.validateDniPacienteIsUnique(createPacienteAgenteRequest.dni());
        pacienteDomainService.validateNumeroTelefonoPacienteNormalizadoIsUnique(numeroTelefono);
        pacienteDomainService.validateEmailPacienteIsUnique(createPacienteAgenteRequest.email());

        //Resolver la cobertura por nombre antes de guardar nada (falla temprano si no existe)
        Plan planDeclarado = createPacienteAgenteRequest.obraSocial() != null
                ? findPlanDeclarado(createPacienteAgenteRequest.obraSocial()) : null;

        //Mapear y guardar el paciente (el teléfono se guarda normalizado: +<dígitos>)
        CreatePacienteRequest createPacienteRequest = new CreatePacienteRequest(
                createPacienteAgenteRequest.dni(), createPacienteAgenteRequest.nombre(),
                createPacienteAgenteRequest.apellido(), createPacienteAgenteRequest.fechaNacimiento(),
                createPacienteAgenteRequest.email(), NormalizadorTelefono.normalizarE164(numeroTelefono), null);
        Paciente pacienteNuevo = pacienteMapper.toEntity(createPacienteRequest);
        Paciente pacienteGuardado = pacienteDomainService.savePaciente(pacienteNuevo);

        //Asignar la cobertura declarada
        if (planDeclarado != null) {
            ObraSocialPaciente obraSocialPacienteNueva = new ObraSocialPaciente(
                    createPacienteAgenteRequest.obraSocial().nroSocio(), pacienteGuardado, planDeclarado);
            obraSocialPacienteDomainService.saveObraSocialPaciente(obraSocialPacienteNueva);
        }

        CreatePacienteAgenteResponse createPacienteAgenteResponse = agenteMapper.toCreatePacienteResponse(pacienteGuardado);
        return createPacienteAgenteResponse;

    }

    /**
     * Modifica los datos del paciente identificado por el teléfono del canal (copia de la
     * orquestación de {@code PacienteApp.updatePaciente}). Si viene una cobertura nueva, reemplaza
     * las coberturas activas del paciente.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param updatePacienteAgenteRequest {@code UpdatePacienteAgenteRequest} datos a modificar
     * @return {@code UpdatePacienteAgenteResponse} datos del paciente actualizados
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no hay un
     *         paciente con ese teléfono, o la obra social o el plan no existen
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el email ya está registrado
     */
    @Transactional
    public UpdatePacienteAgenteResponse updatePaciente(String numeroTelefono,
            UpdatePacienteAgenteRequest updatePacienteAgenteRequest) {

        log.info("Modificación de paciente por canal iniciada");

        //Buscar el paciente activo
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);
        UUID id = pacienteExistente.getId();

        //Validar unicidad del email si vino
        if (updatePacienteAgenteRequest.email() != null) {
            pacienteDomainService.validateEmailPacienteIsUnique(updatePacienteAgenteRequest.email(), id);
        }

        //Resolver la cobertura por nombre antes de modificar nada
        Plan planDeclarado = updatePacienteAgenteRequest.obraSocial() != null
                ? findPlanDeclarado(updatePacienteAgenteRequest.obraSocial()) : null;

        //Aplicar los cambios y guardar (DNI y teléfono no se modifican desde el canal)
        UpdatePacienteRequest updatePacienteRequest = new UpdatePacienteRequest(id, null,
                updatePacienteAgenteRequest.nombre(), updatePacienteAgenteRequest.apellido(),
                updatePacienteAgenteRequest.fechaNacimiento(), updatePacienteAgenteRequest.email(), null);
        pacienteMapper.updatePaciente(pacienteExistente, updatePacienteRequest);
        Paciente pacienteActualizado = pacienteDomainService.savePaciente(pacienteExistente);

        //Reemplazar la cobertura: baja de las activas y alta de la declarada
        if (planDeclarado != null) {
            for (ObraSocialPaciente coberturaActiva : obraSocialPacienteDomainService.findCoberturasActivasByPaciente(id)) {
                obraSocialPacienteDomainService.softDeleteObraSocialPaciente(coberturaActiva,
                        "Reemplazo de cobertura desde el chatbot");
            }
            obraSocialPacienteDomainService.validateSinCoberturaActiva(id, planDeclarado.getId());
            ObraSocialPaciente obraSocialPacienteNueva = new ObraSocialPaciente(
                    updatePacienteAgenteRequest.obraSocial().nroSocio(), pacienteActualizado, planDeclarado);
            obraSocialPacienteDomainService.saveObraSocialPaciente(obraSocialPacienteNueva);
        }

        //Devolver response mapeado, con las coberturas del paciente
        List<ObraSocialAgenteResponse> obrasSocialesResponse = agenteMapper.toObraSocialResponses(
                obraSocialPacienteDomainService.findCoberturasActivasByPaciente(id));
        UpdatePacienteAgenteResponse updatePacienteAgenteResponse =
                agenteMapper.toUpdatePacienteResponse(pacienteActualizado, obrasSocialesResponse);
        return updatePacienteAgenteResponse;

    }

    /**
     * Da de baja al paciente identificado por el teléfono del canal (copia de la orquestación de
     * {@code PacienteApp.softDeletePaciente}): baja lógica restrictiva contra turnos vivos.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no hay un
     *         paciente activo con ese teléfono
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el paciente tiene turnos vivos
     */
    @Transactional
    public void softDeletePaciente(String numeroTelefono) {

        log.info("Baja de paciente por canal iniciada");

        //Buscar el paciente activo
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);

        //Validar que no tenga turnos vivos
        turnoDomainService.validateSinTurnosVivosDePaciente(pacienteExistente.getId(), clinicaDomainService.findZonaHorariaClinica());

        //Dar de baja
        pacienteDomainService.softDeletePaciente(pacienteExistente, "Baja solicitada por el paciente desde el chatbot");

    }

    //endregion

    //region ========== Información general ==========

    /**
     * Obtiene la información general de la clínica.
     *
     * @return {@code GetClinicaAgenteResponse} nombre, ubicación y horario de atención
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si falta la
     *         configuración de la clínica
     */
    @Transactional(readOnly = true)
    public GetClinicaAgenteResponse getClinica() {

        log.info("Consulta de información de la clínica por canal");

        GetClinicaAgenteResponse getClinicaAgenteResponse = agenteMapper.toGetClinicaResponse(clinicaDomainService.findClinica());
        return getClinicaAgenteResponse;

    }

    /**
     * Lista las especialidades activas.
     *
     * @return {@code List<ListEspecialidadAgenteResponse>} especialidades con su nombre
     */
    @Transactional(readOnly = true)
    public List<ListEspecialidadAgenteResponse> listEspecialidades() {

        log.info("Listado de especialidades por canal");

        List<ListEspecialidadAgenteResponse> listEspecialidadAgenteResponses =
                agenteMapper.toListEspecialidadResponses(especialidadDomainService.findEspecialidadesActivas());
        return listEspecialidadAgenteResponses;

    }

    /**
     * Lista las obras sociales activas con los planes que el paciente puede declarar como
     * cobertura al registrarse o modificar sus datos (los no deshabilitados, mismo criterio que
     * resuelve {@code ObraSocialAgenteRequest}). Las obras sociales sin planes asociables se omiten.
     *
     * @return {@code List<ListObraSocialAgenteResponse>} obras sociales con los nombres de sus planes
     */
    @Transactional(readOnly = true)
    public List<ListObraSocialAgenteResponse> listObrasSociales() {

        log.info("Listado de obras sociales por canal");

        //Juntar cada obra social activa con sus planes asociables
        List<ListObraSocialAgenteResponse> listObraSocialAgenteResponses = obraSocialDomainService.findObrasSocialesActivas()
                .stream()
                .map(obraSocial -> agenteMapper.toListObraSocialResponse(obraSocial,
                        planDomainService.findPlanesNoDeshabilitadosByObraSocial(obraSocial.getId())))
                //Una obra social sin planes asociables no se puede declarar
                .filter(listObraSocialAgenteResponse -> !listObraSocialAgenteResponse.planes().isEmpty())
                .toList();
        return listObraSocialAgenteResponses;

    }

    /**
     * Obtiene la oferta de una especialidad: sus prestaciones publicadas y sus médicos activos.
     *
     * @param nombreEspecialidad {@code String} nombre de la especialidad
     * @return {@code GetOfertaEspecialidadAgenteResponse} prestaciones y médicos de la especialidad
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la especialidad
     *         no existe
     */
    @Transactional(readOnly = true)
    public GetOfertaEspecialidadAgenteResponse getOfertaEspecialidad(String nombreEspecialidad) {

        log.info("Consulta de oferta de especialidad por canal: especialidad={}", nombreEspecialidad);

        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadActivaByNombre(nombreEspecialidad);

        GetOfertaEspecialidadAgenteResponse getOfertaEspecialidadAgenteResponse = agenteMapper.toGetOfertaEspecialidadResponse(
                especialidadExistente.getNombre(),
                agenteMapper.toListPrestacionResponses(
                        prestacionDomainService.findPrestacionesPublicadasByEspecialidadId(especialidadExistente.getId())),
                agenteMapper.toListMedicoResponses(
                        medicoDomainService.findMedicosActivosByEspecialidadId(especialidadExistente.getId())));
        return getOfertaEspecialidadAgenteResponse;

    }

    //endregion

    //region ========== Turnos ==========

    /**
     * Lista los horarios disponibles de un médico para una prestación, opcionalmente en una
     * franja del día. Valida que el médico atienda esa prestación y delega la búsqueda en
     * {@code AgendaHorariosDiaQueryService.findHorariosDisponibles}.
     *
     * @param nombrePrestacion {@code String} nombre de la prestación
     * @param matricula {@code String} matrícula del médico
     * @param franjaHorariaAgente {@code FranjaHorariaAgente} franja del día, o {@code null} para todo el día
     * @param pageable {@code Pageable} paginación
     * @return {@code List<ListHorarioDisponibleAgenteResponse>} horarios disponibles (puede estar vacía)
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la prestación o el médico no existen
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el médico no atiende esa prestación
     */
    @Transactional(readOnly = true)
    public List<ListHorarioDisponibleAgenteResponse> listHorariosDisponibles(String nombrePrestacion, String matricula,
            FranjaHorariaAgente franjaHorariaAgente, Pageable pageable) {

        log.info("Consulta de horarios disponibles por canal: prestación={}, matrícula={}", nombrePrestacion, matricula);

        //Resolver prestación y médico por clave de negocio
        Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaByNombre(nombrePrestacion);
        Medico medicoExistente = medicoDomainService.findMedicoActivoByMatricula(matricula);

        //Validar que el médico atienda esa prestación
        if (!medicoPrestacionDomainService.existsVigenteEnFecha(
                medicoExistente.getId(), prestacionExistente.getId(), LocalDate.now())) {
            log.warn("El médico {} no atiende la prestación {}", medicoExistente.getId(), prestacionExistente.getId());
            throw new ReglaNegocioException(getClass(), "MEDICO_NO_ATIENDE_PRESTACION",
                    "El médico elegido no atiende esa prestación.");
        }

        //Armar el criteria con médico, prestación y franja
        AgendaHorariosCriteria agendaHorariosCriteria = new AgendaHorariosCriteria();
        UUIDFilter medicoFilter = new UUIDFilter();
        medicoFilter.setEquals(medicoExistente.getId());
        agendaHorariosCriteria.setMedicoId(medicoFilter);
        UUIDFilter prestacionFilter = new UUIDFilter();
        prestacionFilter.setEquals(prestacionExistente.getId());
        agendaHorariosCriteria.setPrestacionId(prestacionFilter);
        if (franjaHorariaAgente != null) {
            LocalTimeFilter horaDesdeFilter = new LocalTimeFilter();
            horaDesdeFilter.setGreaterThanOrEqual(franjaHorariaAgente.desde());
            horaDesdeFilter.setLessThan(franjaHorariaAgente.hasta());
            agendaHorariosCriteria.setHoraDesde(horaDesdeFilter);
        }

        PageResponse<ListHorarioDisponibleResponse> horariosDisponibles =
                agendaHorariosDiaQueryService.findHorariosDisponibles(agendaHorariosCriteria, pageable);

        List<ListHorarioDisponibleAgenteResponse> listHorarioDisponibleAgenteResponses = horariosDisponibles.content().stream()
                .map(agenteMapper::toListHorarioDisponibleResponse)
                .toList();
        return listHorarioDisponibleAgenteResponses;

    }

    /**
     * Crea un turno para el paciente del canal en el horario elegido (copia de la orquestación de
     * {@code TurnoApp.createTurno}). El médico y la prestación se derivan del slot; la cobertura es
     * la primera obra social activa del paciente cuyo plan cubre la prestación, o particular.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param createTurnoAgenteRequest {@code CreateTurnoAgenteRequest} horario elegido
     * @return {@code CreateTurnoAgenteResponse} el turno creado con su estado inicial
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente o el
     *         horario no existen o ya no están disponibles
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación no está publicada,
     *         el plazo de reserva venció o el médico no atiende esa prestación
     */
    @Transactional
    public CreateTurnoAgenteResponse createTurno(String numeroTelefono, CreateTurnoAgenteRequest createTurnoAgenteRequest) {

        //Buscar paciente (identidad del canal)
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);

        log.info("Creación de turno por canal iniciada: paciente={}, slot={}",
                pacienteExistente.getId(), createTurnoAgenteRequest.slotId());

        //Resolver médico y prestación a partir del slot
        AgendaHorariosDia slotElegido = agendaHorariosDiaDomainService.findAgendaHorarioActivoById(createTurnoAgenteRequest.slotId());
        UUID medicoId = slotElegido.getAgendaMedico().getMedico().getId();
        UUID prestacionId = slotElegido.getPrestacion().getId();

        //Buscar médico activo
        var medicoExistente = medicoDomainService.findMedicoActivoById(medicoId);

        //Buscar prestación activa y validar que esté publicada
        var prestacionExistente = prestacionDomainService.findPrestacionActivaById(prestacionId);
        var estadoVigentePrestacion = historicoEstadoPrestacionDomainService.getEstadoVigente(prestacionId);
        if (estadoVigentePrestacion != EstadoPrestacion.PUBLICADA) {
            log.warn("No se pudo crear turno: prestación {} no está publicada, estado={}", prestacionId, estadoVigentePrestacion);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_PUBLICADA",
                    "La prestación no está disponible para reservar turnos en este momento.");
        }

        //Buscar y validar slot disponible
        var slotDisponible = agendaHorariosDiaDomainService.findAgendaHorarioDisponible(
                createTurnoAgenteRequest.slotId(), medicoId, prestacionId);
        ZonedDateTime ahora = ZonedDateTime.now();
        if (ahora.isAfter(slotDisponible.getFechaLimiteReserva())) {
            log.warn("No se pudo crear turno: slot {} tiene plazo vencido", createTurnoAgenteRequest.slotId());
            throw new ReglaNegocioException(getClass(), "AGENDA_HORARIO_FUERA_DE_PLAZO",
                    "Ya pasó el plazo para reservar este horario. Elegí otro horario.");
        }

        //Validar que el médico atienda esa prestación en esa fecha (y obtener la entidad para el precio)
        var medicoPrestacionExistente = medicoPrestacionDomainService.findVigenteEnFecha(
                medicoId, prestacionId, slotDisponible.getFecha());

        //Obtener indicaciones vigentes de la prestación (para copiar y decidir estado inicial)
        var indicacionesVigentes = indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(prestacionId);
        boolean requiereValidacionAlguna = indicacionesVigentes.stream()
                .anyMatch(IndicacionPrestacion::getRequiereValidacion);

        //Marcar slot ocupado
        agendaHorariosDiaDomainService.occupyAgendaHorario(slotDisponible);

        //Construir y guardar el Turno
        var turnoNuevo = new Turno();
        turnoNuevo.setCodigo(generarCodigoTurno());
        turnoNuevo.setFechaHoraInicio(slotDisponible.getFechaLimiteReserva()
                .plus(prestacionExistente.getTiempoToleranciaSolicitud()));
        turnoNuevo.setPaciente(pacienteExistente);
        turnoNuevo.setMedico(medicoExistente);
        turnoNuevo.setPrestacion(prestacionExistente);
        turnoNuevo.setAgendaHorarios(slotDisponible);

        //Determinar tipo de cobertura (la del paciente que cubre la prestación) y calcular monto a pagar
        ObraSocialPaciente obraSocialPacienteCubridora = findObraSocialPacienteCubridora(pacienteExistente.getId(), prestacionId);
        BigDecimal montoAPagar;
        if (obraSocialPacienteCubridora != null) {
            ObraSocialPlanPrestacion coberturaExistente = obraSocialPlanPrestacionDomainService
                    .findCoberturaByPlanAndPrestacion(obraSocialPacienteCubridora.getPlan().getId(), prestacionId);

            EstrategiaCalcularMontoAPagarTurno estrategia =
                    fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(coberturaExistente.getModalidadCobertura());
            montoAPagar = estrategia.calcularMonto(new ContextoCalculoMontoTurno(
                    medicoPrestacionExistente.getPrecioParticular(),
                    coberturaExistente.getPorcentajeCobertura(),
                    coberturaExistente.getCoseguro()));

            turnoNuevo.setObraSocialPaciente(obraSocialPacienteCubridora);
            turnoNuevo.setTipoCobertura(TipoCobertura.OBRA_SOCIAL);
        } else {
            montoAPagar = medicoPrestacionExistente.getPrecioParticular();
            turnoNuevo.setTipoCobertura(TipoCobertura.PARTICULAR);
        }
        turnoNuevo.setMontoAPagar(montoAPagar);

        //Calcular fechas límite usando tolerancias de la prestación
        turnoNuevo.setFechaLimiteValidacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaValidacion()));
        turnoNuevo.setFechaLimiteReprogramacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaReprogramacion()));
        turnoNuevo.setFechaLimiteConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaConfirmacion()));
        turnoNuevo.setFechaLimiteCancelacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaCancelacion()));
        turnoNuevo.setFechaLimiteAnuncioTemprano(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoToleranciaAnuncio()));
        turnoNuevo.setFechaLimiteAnuncioTardio(turnoNuevo.getFechaHoraInicio()
                .plus(prestacionExistente.getTiempoToleranciaAnuncio()));
        turnoNuevo.setFechaHoraRecordatorioConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(prestacionExistente.getTiempoRecordatorioConfirmacion()));

        var turnoGuardado = turnoDomainService.saveTurno(turnoNuevo);

        //Guardar indicaciones del turno (copia de las vigentes, vinculadas a indicacionPrestacion)
        List<IndicacionPrestacionTurno> indicacionesTurnoNuevas = new ArrayList<>();
        for (IndicacionPrestacion indicacion : indicacionesVigentes) {
            var indicacionTurnoNueva = new IndicacionPrestacionTurno();
            indicacionTurnoNueva.setTurno(turnoGuardado);
            indicacionTurnoNueva.setIndicacionPrestacion(indicacion);
            indicacionesTurnoNuevas.add(indicacionTurnoNueva);
        }
        if (!indicacionesTurnoNuevas.isEmpty()) {
            indicacionPrestacionTurnoDomainService.saveAllIndicacionesPrestacionTurno(indicacionesTurnoNuevas);
        }

        //Abrir estado inicial del turno
        var estadoInicial = requiereValidacionAlguna ? EstadoTurno.ESPERA_VALIDACION : EstadoTurno.PENDIENTE;
        historicoEstadoTurnoDomainService.setEstadoInicialTurno(turnoGuardado, estadoInicial);

        //Mapear y devolver
        CreateTurnoAgenteResponse createTurnoAgenteResponse = agenteMapper.toCreateTurnoResponse(turnoGuardado, estadoInicial, clinicaDomainService.findZonaHorariaClinica());

        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.REGISTRADO, turnoGuardado));
        return createTurnoAgenteResponse;

    }

    /**
     * Lista los turnos del paciente del canal que admiten una acción (cancelar, reprogramar o
     * confirmar), según su estado vigente y su plazo: deja afuera los turnos cuya fecha límite para la
     * acción ya venció (misma comparación que las operaciones). Siempre filtra por el paciente del teléfono.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param accionTurnoAgente {@code AccionTurnoAgente} acción que el paciente quiere hacer
     * @return {@code List<ListTurnoAgenteResponse>} turnos del paciente que admiten la acción y están en plazo (puede estar vacía)
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no hay un
     *         paciente activo con ese teléfono
     */
    @Transactional(readOnly = true)
    public List<ListTurnoAgenteResponse> listTurnos(String numeroTelefono, AccionTurnoAgente accionTurnoAgente) {

        log.info("Listado de turnos por canal iniciado: acción={}", accionTurnoAgente);

        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);

        List<Turno> turnosDelPaciente = turnoDomainService.findTurnosByPacienteIdAndEstadosVigentes(
                pacienteExistente.getId(), accionTurnoAgente.estados());
        Map<UUID, EstadoTurno> estadosVigentes = historicoEstadoTurnoDomainService.getEstadosVigentes(
                turnosDelPaciente.stream().map(Turno::getId).toList());

        //Dejar afuera los turnos cuyo plazo para la acción ya venció
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnosEnPlazo = turnosDelPaciente.stream()
                .filter(turno -> !ahora.isAfter(accionTurnoAgente.fechaLimite(turno)))
                .toList();
        log.debug("Listado de turnos por canal: {} de {} turnos dentro de plazo para {}",
                turnosEnPlazo.size(), turnosDelPaciente.size(), accionTurnoAgente);

        ZoneId zonaHorariaClinica = clinicaDomainService.findZonaHorariaClinica();
        List<ListTurnoAgenteResponse> listTurnoAgenteResponses = turnosEnPlazo.stream()
                .map(turno -> agenteMapper.toListTurnoResponse(turno, estadosVigentes.get(turno.getId()), zonaHorariaClinica))
                .toList();
        return listTurnoAgenteResponses;

    }

    /**
     * Cancela un turno del paciente del canal (copia de la orquestación de {@code TurnoApp.cancelTurno},
     * sin scope de médico): el motivo siempre es {@code SOLICITUD_DEL_PACIENTE}, por lo que se
     * respeta el plazo de cancelación.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param codigo {@code String} código del turno a cancelar
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente o el turno no existen
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el turno es de otro paciente,
     *         está en estado final o el plazo de cancelación venció
     */
    @Transactional
    public void cancelTurno(String numeroTelefono, String codigo) {

        log.info("Cancelación de turno por canal iniciada: código={}", codigo);

        //Resolver paciente y turno, y validar que el turno sea del paciente
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);
        var turnoExistente = turnoDomainService.findTurnoByCodigo(codigo);
        turnoDomainService.validateTurnoPerteneceAPaciente(turnoExistente, pacienteExistente);

        //Validar plazo de cancelación (el motivo siempre es la solicitud del paciente)
        ZonedDateTime ahora = ZonedDateTime.now();
        if (ahora.isAfter(turnoExistente.getFechaLimiteCancelacion())) {
            log.warn("No se pudo cancelar turno {}: plazo de cancelación vencido", codigo);
            throw new ReglaNegocioException(getClass(), "TURNO_FUERA_DE_PLAZO_CANCELACION",
                    "Ya pasó el plazo para cancelar este turno.");
        }

        //Liberar el slot
        agendaHorariosDiaDomainService.releaseAgendaHorario(turnoExistente.getAgendaHorarios());

        turnoExistente.setMotivoCancelacion(MotivoCancelacion.SOLICITUD_DEL_PACIENTE);
        turnoDomainService.saveTurno(turnoExistente);

        //Transicionar a CANCELADO (valida que no esté en estado final)
        historicoEstadoTurnoDomainService.transitionToCanceladoTurno(turnoExistente);

        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.CANCELADO, turnoExistente));

    }

    /**
     * Reprograma un turno del paciente del canal en otro horario del mismo médico y prestación
     * (copia de la orquestación de {@code TurnoApp.reprogramTurno}, sin scope de médico).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param codigo {@code String} código del turno a reprogramar
     * @param reprogramTurnoAgenteRequest {@code ReprogramTurnoAgenteRequest} nuevo horario elegido
     * @return {@code ReprogramTurnoAgenteResponse} el turno nuevo enlazado al original
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente, el
     *         turno o el nuevo horario no existen o no están disponibles
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el turno es de otro paciente,
     *         el plazo de reprogramación venció, el plan de la cobertura no está publicado o el
     *         turno está en un estado que no lo permite
     */
    @Transactional
    public ReprogramTurnoAgenteResponse reprogramTurno(String numeroTelefono, String codigo,
            ReprogramTurnoAgenteRequest reprogramTurnoAgenteRequest) {

        log.info("Reprogramación de turno por canal iniciada: código={}, slot={}",
                codigo, reprogramTurnoAgenteRequest.slotId());

        //Resolver paciente y turno viejo, y validar que el turno sea del paciente
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);
        var turnoViejo = turnoDomainService.findTurnoByCodigo(codigo);
        turnoDomainService.validateTurnoPerteneceAPaciente(turnoViejo, pacienteExistente);

        //Validar que no haya vencido el plazo de reprogramación
        ZonedDateTime ahora = ZonedDateTime.now();
        if (ahora.isAfter(turnoViejo.getFechaLimiteReprogramacion())) {
            log.warn("No se pudo reprogramar turno {}: plazo de reprogramación vencido", codigo);
            throw new ReglaNegocioException(getClass(), "TURNO_FUERA_DE_PLAZO_REPROGRAMACION",
                    "Ya pasó el plazo para reprogramar este turno.");
        }

        //Validar el nuevo slot contra los mismos criterios que createTurno
        var slotNuevo = agendaHorariosDiaDomainService.findAgendaHorarioDisponible(
                reprogramTurnoAgenteRequest.slotId(), turnoViejo.getMedico().getId(), turnoViejo.getPrestacion().getId());
        if (ahora.isAfter(slotNuevo.getFechaLimiteReserva())) {
            log.warn("No se pudo reprogramar turno {}: nuevo slot {} tiene plazo vencido",
                    codigo, reprogramTurnoAgenteRequest.slotId());
            throw new ReglaNegocioException(getClass(), "AGENDA_HORARIO_FUERA_DE_PLAZO",
                    "Ya pasó el plazo para reservar el nuevo horario. Elegí otro horario.");
        }

        //Revalidar que el médico siga atendiendo esa prestación en la nueva fecha (y obtener la entidad para el precio)
        var medicoPrestacionVigenteEnFecha = medicoPrestacionDomainService.findVigenteEnFecha(
                turnoViejo.getMedico().getId(), turnoViejo.getPrestacion().getId(),
                slotNuevo.getFecha());

        //Revalidar que la prestación siga publicada
        var estadoVigentePrestacion = historicoEstadoPrestacionDomainService.getEstadoVigente(turnoViejo.getPrestacion().getId());
        if (estadoVigentePrestacion != EstadoPrestacion.PUBLICADA) {
            log.warn("No se pudo reprogramar turno {}: prestación {} no está publicada",
                    codigo, turnoViejo.getPrestacion().getId());
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_PUBLICADA",
                    "La prestación no está disponible para reservar turnos en este momento.");
        }

        //Recalcular indicaciones (por si cambiaron)
        var indicacionesVigentes = indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(
                turnoViejo.getPrestacion().getId());
        boolean requiereValidacionAlguna = indicacionesVigentes.stream()
                .anyMatch(IndicacionPrestacion::getRequiereValidacion);

        //Ocupar el nuevo slot y liberar el viejo
        agendaHorariosDiaDomainService.occupyAgendaHorario(slotNuevo);
        agendaHorariosDiaDomainService.releaseAgendaHorario(turnoViejo.getAgendaHorarios());

        //Crear el turno nuevo enlazado por turnoOrigen
        var turnoNuevo = new Turno();
        turnoNuevo.setCodigo(generarCodigoTurno());
        turnoNuevo.setFechaHoraInicio(slotNuevo.getFechaLimiteReserva()
                .plus(turnoViejo.getPrestacion().getTiempoToleranciaSolicitud()));
        turnoNuevo.setPaciente(turnoViejo.getPaciente());
        turnoNuevo.setMedico(turnoViejo.getMedico());
        turnoNuevo.setPrestacion(turnoViejo.getPrestacion());
        turnoNuevo.setAgendaHorarios(slotNuevo);
        turnoNuevo.setTurnoOrigen(turnoViejo);

        //Recalcular tipo de cobertura y monto a pagar (por si cambiaron entre reprogramaciones)
        BigDecimal montoAPagarReprogramado;
        if (turnoViejo.getObraSocialPaciente() != null) {
            //Solo un plan publicado cubre: si dejó de estarlo, no se reprograma
            historicoEstadoPlanDomainService.validatePlanPublicado(turnoViejo.getObraSocialPaciente().getPlan());

            ObraSocialPlanPrestacion coberturaActual = obraSocialPlanPrestacionDomainService
                    .findCoberturaByPlanAndPrestacion(turnoViejo.getObraSocialPaciente().getPlan().getId(),
                            turnoViejo.getPrestacion().getId());

            EstrategiaCalcularMontoAPagarTurno estrategia =
                    fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(coberturaActual.getModalidadCobertura());
            montoAPagarReprogramado = estrategia.calcularMonto(new ContextoCalculoMontoTurno(
                    medicoPrestacionVigenteEnFecha.getPrecioParticular(),
                    coberturaActual.getPorcentajeCobertura(),
                    coberturaActual.getCoseguro()));

            turnoNuevo.setObraSocialPaciente(turnoViejo.getObraSocialPaciente());
            turnoNuevo.setTipoCobertura(TipoCobertura.OBRA_SOCIAL);
        } else {
            montoAPagarReprogramado = medicoPrestacionVigenteEnFecha.getPrecioParticular();
            turnoNuevo.setTipoCobertura(TipoCobertura.PARTICULAR);
        }
        turnoNuevo.setMontoAPagar(montoAPagarReprogramado);

        //Copiar fechas límite del viejo (mismo médico, prestación, tipo de cobertura)
        turnoNuevo.setFechaLimiteValidacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaValidacion()));
        turnoNuevo.setFechaLimiteReprogramacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaReprogramacion()));
        turnoNuevo.setFechaLimiteConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaConfirmacion()));
        turnoNuevo.setFechaLimiteCancelacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaCancelacion()));
        turnoNuevo.setFechaLimiteAnuncioTemprano(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoToleranciaAnuncio()));
        turnoNuevo.setFechaLimiteAnuncioTardio(turnoNuevo.getFechaHoraInicio()
                .plus(turnoViejo.getPrestacion().getTiempoToleranciaAnuncio()));
        turnoNuevo.setFechaHoraRecordatorioConfirmacion(turnoNuevo.getFechaHoraInicio()
                .minus(turnoViejo.getPrestacion().getTiempoRecordatorioConfirmacion()));

        var turnoNuevoGuardado = turnoDomainService.saveTurno(turnoNuevo);

        //Transicionar el turno viejo a REPROGRAMADO
        historicoEstadoTurnoDomainService.transitionToReprogramadoTurno(turnoViejo);

        //Abrir estado inicial del turno nuevo
        var estadoInicial = requiereValidacionAlguna ? EstadoTurno.ESPERA_VALIDACION : EstadoTurno.PENDIENTE;
        historicoEstadoTurnoDomainService.setEstadoInicialTurno(turnoNuevoGuardado, estadoInicial);

        //Mapear y devolver
        ReprogramTurnoAgenteResponse reprogramTurnoAgenteResponse =
                agenteMapper.toReprogramTurnoResponse(turnoNuevoGuardado, estadoInicial, clinicaDomainService.findZonaHorariaClinica());

        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.REPROGRAMADO, turnoNuevoGuardado));
        return reprogramTurnoAgenteResponse;

    }

    /**
     * Confirma un turno del paciente del canal (copia de la orquestación de {@code TurnoApp.confirmTurno}).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal
     * @param codigo {@code String} código del turno a confirmar
     * @return {@code ConfirmTurnoAgenteResponse} el turno confirmado
     * @throws ValidacionException {@code ValidacionException} si el teléfono no es válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente o el turno no existen
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el turno es de otro paciente,
     *         el plazo de confirmación venció o el turno no está pendiente
     */
    @Transactional
    public ConfirmTurnoAgenteResponse confirmTurno(String numeroTelefono, String codigo) {

        log.info("Confirmación de turno por canal iniciada: código={}", codigo);

        //Resolver paciente y turno, y validar que el turno sea del paciente
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoByNumeroTelefono(numeroTelefono);
        var turnoExistente = turnoDomainService.findTurnoByCodigo(codigo);
        turnoDomainService.validateTurnoPerteneceAPaciente(turnoExistente, pacienteExistente);

        //Validar que el plazo de confirmación no haya vencido
        ZonedDateTime ahora = ZonedDateTime.now();
        if (ahora.isAfter(turnoExistente.getFechaLimiteConfirmacion())) {
            log.warn("No se pudo confirmar turno {}: plazo de confirmación vencido", codigo);
            throw new ReglaNegocioException(getClass(), "TURNO_FUERA_DE_PLAZO_CONFIRMACION",
                    "Ya pasó el plazo para confirmar este turno.");
        }

        historicoEstadoTurnoDomainService.transitionPendienteToConfirmadoTurno(turnoExistente);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.CONFIRMADO, turnoExistente));

        ConfirmTurnoAgenteResponse confirmTurnoAgenteResponse =
                agenteMapper.toConfirmTurnoResponse(turnoExistente, EstadoTurno.CONFIRMADO, clinicaDomainService.findZonaHorariaClinica());
        return confirmTurnoAgenteResponse;

    }

    //endregion

    //region ========== Helpers privados ==========

    /**
     * Resuelve la cobertura declarada por nombres: busca la obra social activa y el plan activo
     * de esa obra social.
     *
     * @param obraSocialAgenteRequest {@code ObraSocialAgenteRequest} obra social y plan declarados
     * @return {@code Plan} el plan declarado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social
     *         o el plan no existen
     */
    private Plan findPlanDeclarado(ObraSocialAgenteRequest obraSocialAgenteRequest) {

        ObraSocial obraSocialExistente = obraSocialDomainService
                .findObraSocialActivaByNombre(obraSocialAgenteRequest.nombreObraSocial());

        return planDomainService.findPlanActivoByObraSocialIdAndNombre(
                obraSocialExistente.getId(), obraSocialAgenteRequest.nombrePlan());

    }

    /**
     * Busca la primera cobertura activa del paciente cuyo plan está publicado y cubre la prestación.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code ObraSocialPaciente} la cobertura que cubre la prestación, o {@code null} si
     *         ninguna la cubre (el turno será particular)
     */
    private ObraSocialPaciente findObraSocialPacienteCubridora(UUID pacienteId, UUID prestacionId) {

        for (ObraSocialPaciente cobertura : obraSocialPacienteDomainService.findCoberturasActivasByPaciente(pacienteId)) {
            if (historicoEstadoPlanDomainService.isPlanPublicado(cobertura.getPlan().getId())
                    && obraSocialPlanPrestacionDomainService.existsCoberturaActivaByPlanAndPrestacion(
                            cobertura.getPlan().getId(), prestacionId)) {
                return cobertura;
            }
        }

        return null;

    }

    /**
     * Genera el código de negocio de un turno nuevo (copia del helper de {@code TurnoApp}).
     *
     * @return {@code String} código con el formato {@code TURNO_XXXXXXXX}
     */
    private String generarCodigoTurno() {
        return "TURNO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    //endregion

}
