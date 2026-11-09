package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.CotizacionAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.DisponibilidadAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.EspecialidadAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.HorarioAfirmado;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.MedicoAfirmado;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.ObraSocialAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.PrestacionAfirmada;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest.TurnoAfirmado;
import com.accesmed.backend.Records.Agente.Response.VerifyRespuestaAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.VerifyRespuestaAgenteResponse.ItemVerificado;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoTurnoDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.QueryServices.AgendaHorariosDiaQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.LocalDateFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.LocalTimeFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import com.accesmed.backend.Services.Utils.FormatoMensaje;
import com.accesmed.backend.Services.Utils.NormalizadorBusqueda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Verificación de las respuestas del agente (capa {@code Services/AgenteServices},
 * {@code Docs/Diseno-Chatbot.md §4.3}): contrasta contra la base cada afirmación que el agente
 * declara (especialidades, prestaciones, médicos, horarios, disponibilidad, turnos, cotizaciones, obras sociales y planes)
 * y devuelve un veredicto por ítem, con el motivo y el valor correcto cuando se puede dar. Solo lee.
 * <p>
 * Lo usa únicamente {@code AgenteApp}. Con la property {@code accesmed.agente.verificacion.habilitada}
 * en {@code false} da todo por verdadero sin consultar la base (interruptor para apagarla si en
 * staging da falsos positivos).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificacionAgenteService {

    //region ========== Constantes ==========

    private static final int TAMANIO_PAGINA_HORARIOS = 500;

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadDomainService especialidadDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final MedicoDomainService medicoDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    private final TurnoDomainService turnoDomainService;
    private final PacienteDomainService pacienteDomainService;
    private final ClinicaDomainService clinicaDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanDomainService planDomainService;
    private final AgendaHorariosDiaQueryService agendaHorariosDiaQueryService;
    private final CotizacionAgenteService cotizacionAgenteService;

    /**
     * Interruptor de la verificación: en {@code false} todo ítem se da por verdadero.
     */
    @Value("${accesmed.agente.verificacion.habilitada:true}")
    private boolean verificacionHabilitada;

    //endregion

    //region ========== Métodos ==========

    /**
     * Verifica las afirmaciones de una respuesta del agente contra la base.
     *
     * @param numeroTelefono {@code String} teléfono del canal (identifica al paciente para turnos y cotizaciones), o {@code null}
     * @param verifyRespuestaAgenteRequest {@code VerifyRespuestaAgenteRequest} afirmaciones agrupadas por entidad
     * @return {@code VerifyRespuestaAgenteResponse} veredicto por ítem y global
     */
    public VerifyRespuestaAgenteResponse verifyRespuesta(String numeroTelefono,
            VerifyRespuestaAgenteRequest verifyRespuestaAgenteRequest) {

        List<ItemVerificado<EspecialidadAfirmada>> especialidades = verifyItems(
                verifyRespuestaAgenteRequest.especialidades(), this::verifyEspecialidad);
        List<ItemVerificado<PrestacionAfirmada>> prestaciones = verifyItems(
                verifyRespuestaAgenteRequest.prestaciones(), this::verifyPrestacion);
        List<ItemVerificado<MedicoAfirmado>> medicos = verifyItems(
                verifyRespuestaAgenteRequest.medicos(), this::verifyMedico);
        List<ItemVerificado<HorarioAfirmado>> horarios = verifyItems(
                verifyRespuestaAgenteRequest.horarios(), this::verifyHorario);
        List<ItemVerificado<DisponibilidadAfirmada>> disponibilidad = verifyItems(
                verifyRespuestaAgenteRequest.disponibilidad(), this::verifyDisponibilidad);
        List<ItemVerificado<TurnoAfirmado>> turnos = verifyItems(
                verifyRespuestaAgenteRequest.turnos(), turnoAfirmado -> verifyTurno(numeroTelefono, turnoAfirmado));
        List<ItemVerificado<CotizacionAfirmada>> cotizaciones = verifyItems(
                verifyRespuestaAgenteRequest.cotizaciones(), cotizacionAfirmada -> verifyCotizacion(numeroTelefono, cotizacionAfirmada));

        List<ItemVerificado<ObraSocialAfirmada>> obrasSociales = verifyItems(
                verifyRespuestaAgenteRequest.obrasSociales(), this::verifyObraSocial);

        boolean verdadero = todosVerdaderos(especialidades) && todosVerdaderos(prestaciones) && todosVerdaderos(medicos)
                && todosVerdaderos(horarios) && todosVerdaderos(disponibilidad) && todosVerdaderos(turnos)
                && todosVerdaderos(cotizaciones) && todosVerdaderos(obrasSociales);

        VerifyRespuestaAgenteResponse verifyRespuestaAgenteResponse = new VerifyRespuestaAgenteResponse(verdadero,
                especialidades, prestaciones, medicos, horarios, disponibilidad, turnos, cotizaciones, obrasSociales);
        return verifyRespuestaAgenteResponse;

    }

    //endregion

    //region ========== Verificación por entidad ==========

    /**
     * Verifica que la especialidad exista y esté activa.
     *
     * @param especialidadAfirmada {@code EspecialidadAfirmada} especialidad nombrada
     * @return {@code ItemVerificado<EspecialidadAfirmada>} el veredicto
     */
    private ItemVerificado<EspecialidadAfirmada> verifyEspecialidad(EspecialidadAfirmada especialidadAfirmada) {

        Optional<Especialidad> especialidadExistente = findEspecialidadByNombre(especialidadAfirmada.nombre());
        if (especialidadExistente.isEmpty()) {
            return rechazar(especialidadAfirmada, "No existe una especialidad activa llamada \"" + especialidadAfirmada.nombre() + "\".", null);
        }

        return ItemVerificado.aprobado(especialidadAfirmada);

    }

    /**
     * Verifica que la prestación esté publicada y que su especialidad, indicaciones y necesidad de
     * validación coincidan con lo dicho.
     *
     * @param prestacionAfirmada {@code PrestacionAfirmada} prestación nombrada
     * @return {@code ItemVerificado<PrestacionAfirmada>} el veredicto
     */
    private ItemVerificado<PrestacionAfirmada> verifyPrestacion(PrestacionAfirmada prestacionAfirmada) {

        //Existe y está publicada
        Optional<Prestacion> prestacionPublicada = findPrestacionPublicadaByNombre(prestacionAfirmada.nombre());
        if (prestacionPublicada.isEmpty()) {
            return rechazar(prestacionAfirmada, "No hay una prestación publicada llamada \"" + prestacionAfirmada.nombre()
                    + "\". No la ofrezcas ni la nombres.", null);
        }
        Prestacion prestacionExistente = prestacionPublicada.get();

        //Especialidad
        String especialidadReal = prestacionExistente.getEspecialidad().getNombre();
        if (prestacionAfirmada.especialidad() != null && !sonIguales(prestacionAfirmada.especialidad(), especialidadReal)) {
            return rechazar(prestacionAfirmada, "La prestación " + prestacionExistente.getNombre()
                    + " no es de la especialidad " + prestacionAfirmada.especialidad() + ".", especialidadReal);
        }

        //Indicaciones vigentes
        List<IndicacionPrestacion> indicacionesVigentes = indicacionPrestacionDomainService
                .findIndicacionesPrestacionVigentesByPrestacionId(prestacionExistente.getId());
        for (String indicacionAfirmada : orEmpty(prestacionAfirmada.indicaciones())) {
            if (indicacionesVigentes.stream().noneMatch(indicacion -> coincideIndicacion(indicacionAfirmada, indicacion))) {
                return rechazar(prestacionAfirmada, "La indicación \"" + indicacionAfirmada + "\" no es una indicación vigente de "
                        + prestacionExistente.getNombre() + ".", null);
            }
        }

        //Necesidad de validación
        boolean requiereValidacionReal = indicacionesVigentes.stream().anyMatch(IndicacionPrestacion::getRequiereValidacion);
        if (prestacionAfirmada.requiereValidacion() != null && prestacionAfirmada.requiereValidacion() != requiereValidacionReal) {
            return rechazar(prestacionAfirmada, "La prestación " + prestacionExistente.getNombre()
                    + (requiereValidacionReal ? " sí" : " no") + " requiere validación.", String.valueOf(requiereValidacionReal));
        }

        return ItemVerificado.aprobado(prestacionAfirmada);

    }

    /**
     * Verifica que la matrícula sea de un médico activo y que su nombre, especialidad y prestación
     * coincidan con lo dicho.
     *
     * @param medicoAfirmado {@code MedicoAfirmado} médico nombrado
     * @return {@code ItemVerificado<MedicoAfirmado>} el veredicto
     */
    private ItemVerificado<MedicoAfirmado> verifyMedico(MedicoAfirmado medicoAfirmado) {

        //Matrícula activa
        Optional<Medico> medicoActivo = medicoDomainService.findOptionalMedicoActivoByMatricula(medicoAfirmado.matricula());
        if (medicoActivo.isEmpty()) {
            return rechazar(medicoAfirmado, "No hay un médico activo con la matrícula " + medicoAfirmado.matricula() + ".", null);
        }
        Medico medicoExistente = medicoActivo.get();
        String nombreReal = medicoExistente.getNombre() + " " + medicoExistente.getApellido();

        //Nombre: cada palabra dicha tiene que estar en nombre y apellido
        if (medicoAfirmado.nombre() != null) {
            String nombreRealNormalizado = NormalizadorBusqueda.normalizarTexto(nombreReal);
            boolean nombreCoincide = NormalizadorBusqueda.findPalabrasBusqueda(medicoAfirmado.nombre()).stream()
                    .allMatch(nombreRealNormalizado::contains);
            if (!nombreCoincide) {
                return rechazar(medicoAfirmado, "La matrícula " + medicoAfirmado.matricula() + " no es de "
                        + medicoAfirmado.nombre() + ".", nombreReal);
            }
        }

        //Especialidad
        String especialidadReal = medicoExistente.getEspecialidad().getNombre();
        if (medicoAfirmado.especialidad() != null && !sonIguales(medicoAfirmado.especialidad(), especialidadReal)) {
            return rechazar(medicoAfirmado, "El médico " + nombreReal + " no es de la especialidad "
                    + medicoAfirmado.especialidad() + ".", especialidadReal);
        }

        //Prestación que atiende hoy
        if (medicoAfirmado.prestacion() != null) {
            Optional<Prestacion> prestacionPublicada = findPrestacionPublicadaByNombre(medicoAfirmado.prestacion());
            if (prestacionPublicada.isEmpty() || !medicoPrestacionDomainService.existsVigenteEnFecha(
                    medicoExistente.getId(), prestacionPublicada.get().getId(), clinicaDomainService.findFechaActualClinica())) {
                return rechazar(medicoAfirmado, "El médico " + nombreReal + " no atiende " + medicoAfirmado.prestacion() + ".", null);
            }
        }

        return ItemVerificado.aprobado(medicoAfirmado);

    }

    /**
     * Verifica que el día de la semana corresponda a la fecha y que exista un horario libre, dentro
     * del plazo de reserva, para ese médico, prestación, fecha y hora.
     *
     * @param horarioAfirmado {@code HorarioAfirmado} horario ofrecido
     * @return {@code ItemVerificado<HorarioAfirmado>} el veredicto
     */
    private ItemVerificado<HorarioAfirmado> verifyHorario(HorarioAfirmado horarioAfirmado) {

        //Día de la semana
        String diaSemanaReal = FormatoMensaje.diaSemana(horarioAfirmado.fecha());
        if (horarioAfirmado.diaSemana() != null && !sonIguales(horarioAfirmado.diaSemana(), diaSemanaReal)) {
            return rechazar(horarioAfirmado, "El " + FormatoMensaje.fecha(horarioAfirmado.fecha()) + " no es "
                    + horarioAfirmado.diaSemana() + ", es " + diaSemanaReal + ".", diaSemanaReal);
        }

        //Horario libre
        Optional<ListHorarioDisponibleResponse> slotLibre = findSlotLibre(horarioAfirmado.matricula(),
                horarioAfirmado.prestacion(), horarioAfirmado.fecha(), horarioAfirmado.hora());
        if (slotLibre.isEmpty()) {
            return rechazar(horarioAfirmado, "No hay un horario libre el " + FormatoMensaje.diaSemana(horarioAfirmado.fecha())
                    + " " + FormatoMensaje.fecha(horarioAfirmado.fecha()) + " a las " + horarioAfirmado.hora()
                    + " con la matrícula " + horarioAfirmado.matricula() + " para " + horarioAfirmado.prestacion()
                    + ". No lo ofrezcas: consultá los horarios disponibles.", null);
        }

        return ItemVerificado.aprobado(horarioAfirmado);

    }

    /**
     * Verifica un "hay turnos" / "no hay turnos" contra los horarios libres de la base. Según lo que
     * declaró el agente consulta un día ({@code fecha}), una franja del día ({@code franjaHoraria}),
     * ambos, o una hora puntual ({@code hora}, que exige {@code fecha}).
     *
     * @param disponibilidadAfirmada {@code DisponibilidadAfirmada} afirmación de disponibilidad
     * @return {@code ItemVerificado<DisponibilidadAfirmada>} el veredicto
     */
    private ItemVerificado<DisponibilidadAfirmada> verifyDisponibilidad(DisponibilidadAfirmada disponibilidadAfirmada) {

        Optional<Prestacion> prestacionPublicada = findPrestacionPublicadaByNombre(disponibilidadAfirmada.prestacion());
        if (prestacionPublicada.isEmpty()) {
            return rechazar(disponibilidadAfirmada, "No hay una prestación publicada llamada \""
                    + disponibilidadAfirmada.prestacion() + "\".", null);
        }

        //Una hora puntual solo tiene sentido en un día concreto
        if (disponibilidadAfirmada.hora() != null && disponibilidadAfirmada.fecha() == null) {
            return rechazar(disponibilidadAfirmada, "Para afirmar si una hora está libre hay que declarar también la fecha.", null);
        }

        //Médico opcional
        Medico medicoExistente = null;
        if (disponibilidadAfirmada.matricula() != null) {
            Optional<Medico> medicoActivo = medicoDomainService.findOptionalMedicoActivoByMatricula(disponibilidadAfirmada.matricula());
            if (medicoActivo.isEmpty()) {
                return rechazar(disponibilidadAfirmada, "No hay un médico activo con la matrícula "
                        + disponibilidadAfirmada.matricula() + ".", null);
            }
            medicoExistente = medicoActivo.get();
        }

        //Con hora: existe un horario libre justo a esa hora; sin hora: algún horario libre en el día y la franja
        List<ListHorarioDisponibleResponse> slotsLibres = findSlotsLibres(medicoExistente, prestacionPublicada.get(),
                disponibilidadAfirmada.fecha(), disponibilidadAfirmada.hora() != null ? null : disponibilidadAfirmada.franjaHoraria());
        boolean hayReal = disponibilidadAfirmada.hora() != null
                ? slotsLibres.stream().anyMatch(slot -> slot.horaDesde().truncatedTo(ChronoUnit.MINUTES)
                        .equals(disponibilidadAfirmada.hora().truncatedTo(ChronoUnit.MINUTES)))
                : !slotsLibres.isEmpty();

        if (disponibilidadAfirmada.hay() != null && disponibilidadAfirmada.hay() != hayReal) {
            return rechazar(disponibilidadAfirmada, buildMotivoDisponibilidad(disponibilidadAfirmada, hayReal),
                    String.valueOf(hayReal));
        }

        return ItemVerificado.aprobado(disponibilidadAfirmada);

    }

    /**
     * Verifica que la obra social exista y esté activa y que cada plan nombrado sea suyo y esté vigente
     * (no deshabilitado, la misma consulta que alimenta el listado de obras sociales del canal).
     *
     * @param obraSocialAfirmada {@code ObraSocialAfirmada} obra social y planes nombrados
     * @return {@code ItemVerificado<ObraSocialAfirmada>} el veredicto
     */
    private ItemVerificado<ObraSocialAfirmada> verifyObraSocial(ObraSocialAfirmada obraSocialAfirmada) {

        Optional<ObraSocial> obraSocialActiva = obraSocialDomainService.findObrasSocialesActivas().stream()
                .filter(obraSocial -> sonIguales(obraSocial.getNombre(), obraSocialAfirmada.nombre()))
                .findFirst();
        if (obraSocialActiva.isEmpty()) {
            return rechazar(obraSocialAfirmada, "No hay una obra social activa llamada \"" + obraSocialAfirmada.nombre()
                    + "\". No la nombres como aceptada.", null);
        }
        ObraSocial obraSocialExistente = obraSocialActiva.get();

        List<Plan> planesVigentes = planDomainService.findPlanesNoDeshabilitadosByObraSocial(obraSocialExistente.getId());
        for (String planAfirmado : orEmpty(obraSocialAfirmada.planes())) {
            if (planesVigentes.stream().noneMatch(plan -> sonIguales(plan.getNombre(), planAfirmado))) {
                return rechazar(obraSocialAfirmada, "El plan \"" + planAfirmado + "\" no es un plan vigente de "
                        + obraSocialExistente.getNombre() + ".",
                        planesVigentes.stream().map(Plan::getNombre).collect(Collectors.joining(", ")));
            }
        }

        return ItemVerificado.aprobado(obraSocialAfirmada);

    }

    /**
     * Verifica que el turno sea del paciente del canal y que su fecha, hora y estado vigente
     * coincidan con lo dicho.
     *
     * @param numeroTelefono {@code String} teléfono del canal
     * @param turnoAfirmado {@code TurnoAfirmado} turno nombrado
     * @return {@code ItemVerificado<TurnoAfirmado>} el veredicto
     */
    private ItemVerificado<TurnoAfirmado> verifyTurno(String numeroTelefono, TurnoAfirmado turnoAfirmado) {

        Optional<Paciente> pacienteDelCanal = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);
        Optional<Turno> turnoExistente = turnoDomainService.findOptionalTurnoByCodigo(turnoAfirmado.codigo());

        //Tiene que existir y ser del paciente del teléfono (sin revelar turnos ajenos)
        if (pacienteDelCanal.isEmpty() || turnoExistente.isEmpty()
                || !turnoExistente.get().getPaciente().getId().equals(pacienteDelCanal.get().getId())) {
            return rechazar(turnoAfirmado, "El paciente no tiene un turno con el código " + turnoAfirmado.codigo() + ".", null);
        }
        Turno turnoDelPaciente = turnoExistente.get();

        //Fecha y hora en la zona de la clínica
        ZoneId zonaHorariaClinica = clinicaDomainService.findZonaHorariaClinica();
        LocalDate fechaReal = turnoDelPaciente.getFechaHoraInicio().withZoneSameInstant(zonaHorariaClinica).toLocalDate();
        LocalTime horaReal = turnoDelPaciente.getFechaHoraInicio().withZoneSameInstant(zonaHorariaClinica)
                .toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        String fechaHoraReal = FormatoMensaje.diaSemana(fechaReal) + " " + FormatoMensaje.fecha(fechaReal) + " " + horaReal;
        if (turnoAfirmado.fecha() != null && !turnoAfirmado.fecha().equals(fechaReal)) {
            return rechazar(turnoAfirmado, "El turno " + turnoAfirmado.codigo() + " no es el "
                    + FormatoMensaje.fecha(turnoAfirmado.fecha()) + ".", fechaHoraReal);
        }
        if (turnoAfirmado.hora() != null && !turnoAfirmado.hora().truncatedTo(ChronoUnit.MINUTES).equals(horaReal)) {
            return rechazar(turnoAfirmado, "El turno " + turnoAfirmado.codigo() + " no es a las " + turnoAfirmado.hora() + ".", fechaHoraReal);
        }

        //Estado vigente
        EstadoTurno estadoReal = historicoEstadoTurnoDomainService.getEstadoVigente(turnoDelPaciente.getId());
        if (turnoAfirmado.estado() != null
                && !sonIguales(turnoAfirmado.estado(), FormatoMensaje.estado(estadoReal))
                && !sonIguales(turnoAfirmado.estado(), estadoReal.name())) {
            return rechazar(turnoAfirmado, "El estado del turno " + turnoAfirmado.codigo() + " no es "
                    + turnoAfirmado.estado() + ".", FormatoMensaje.estado(estadoReal));
        }

        return ItemVerificado.aprobado(turnoAfirmado);

    }

    /**
     * Verifica que el monto, la cobertura y el plan informados coincidan con la cotización que
     * calcula la base para ese horario y paciente.
     *
     * @param numeroTelefono {@code String} teléfono del canal
     * @param cotizacionAfirmada {@code CotizacionAfirmada} cotización informada
     * @return {@code ItemVerificado<CotizacionAfirmada>} el veredicto
     */
    private ItemVerificado<CotizacionAfirmada> verifyCotizacion(String numeroTelefono, CotizacionAfirmada cotizacionAfirmada) {

        Optional<Paciente> pacienteDelCanal = pacienteDomainService.findOptionalPacienteActivoByNumeroTelefono(numeroTelefono);
        if (pacienteDelCanal.isEmpty()) {
            return rechazar(cotizacionAfirmada, "No se pudo identificar al paciente para verificar el monto.", null);
        }

        //El horario cotizado tiene que existir
        Optional<ListHorarioDisponibleResponse> slotLibre = findSlotLibre(cotizacionAfirmada.matricula(),
                cotizacionAfirmada.prestacion(), cotizacionAfirmada.fecha(), cotizacionAfirmada.hora());
        if (slotLibre.isEmpty()) {
            return rechazar(cotizacionAfirmada, "No hay un horario libre para ese médico, prestación, fecha y hora: no se puede cotizar.", null);
        }

        //Recalcular con el mismo cálculo que la cotización y la reserva
        Prestacion prestacionExistente = findPrestacionPublicadaByNombre(cotizacionAfirmada.prestacion()).orElseThrow();
        MedicoPrestacion medicoPrestacionVigente = medicoPrestacionDomainService.findVigenteEnFecha(
                slotLibre.get().medicoId(), prestacionExistente.getId(), cotizacionAfirmada.fecha());
        CotizacionTurnoAgente cotizacionReal = cotizacionAgenteService.cotizarTurno(pacienteDelCanal.get(),
                prestacionExistente, medicoPrestacionVigente, cotizacionAfirmada.cobertura());

        //Con obra social tiene que cubrir
        if (!cotizacionReal.cubre()) {
            return rechazar(cotizacionAfirmada, cotizacionReal.motivo(), "PARTICULAR " + cotizacionReal.precioParticular());
        }

        //Plan informado
        if (cotizacionReal.obraSocialPaciente() != null && cotizacionAfirmada.plan() != null) {
            String planReal = cotizacionReal.obraSocialPaciente().getPlan().getNombre();
            if (!sonIguales(cotizacionAfirmada.plan(), planReal)) {
                return rechazar(cotizacionAfirmada, "La cobertura del paciente para este turno es el plan " + planReal + ", no "
                        + cotizacionAfirmada.plan() + ".", planReal);
            }
        }

        //Monto
        if (cotizacionAfirmada.monto() != null && cotizacionAfirmada.monto().compareTo(cotizacionReal.montoAPagar()) != 0) {
            return rechazar(cotizacionAfirmada, "El monto a pagar no es " + cotizacionAfirmada.monto() + ".",
                    String.valueOf(cotizacionReal.montoAPagar()));
        }

        return ItemVerificado.aprobado(cotizacionAfirmada);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Verifica una lista de afirmaciones (puede ser {@code null}). Con la verificación apagada
     * todo ítem se da por verdadero sin consultar la base.
     *
     * @param afirmaciones {@code List<T>} afirmaciones a verificar, o {@code null}
     * @param verificador {@code Function<T, ItemVerificado<T>>} verificación de un ítem
     * @param <T> tipo de la afirmación
     * @return {@code List<ItemVerificado<T>>} un veredicto por afirmación
     */
    private <T> List<ItemVerificado<T>> verifyItems(List<T> afirmaciones, Function<T, ItemVerificado<T>> verificador) {

        List<ItemVerificado<T>> itemsVerificados = orEmpty(afirmaciones).stream()
                .map(afirmacion -> verificacionHabilitada ? verificador.apply(afirmacion) : ItemVerificado.aprobado(afirmacion))
                .toList();
        return itemsVerificados;

    }

    /**
     * Arma un veredicto falso y deja constancia del rechazo (sirve para medir falsos positivos).
     *
     * @param afirmacion {@code T} afirmación rechazada
     * @param motivo {@code String} por qué es falsa
     * @param correcto {@code String} valor real, o {@code null}
     * @param <T> tipo de la afirmación
     * @return {@code ItemVerificado<T>} ítem falso
     */
    private <T> ItemVerificado<T> rechazar(T afirmacion, String motivo, String correcto) {

        log.warn("Afirmación del agente rechazada: {} → {}", afirmacion, motivo);

        ItemVerificado<T> itemFalso = ItemVerificado.rechazado(afirmacion, motivo, correcto);
        return itemFalso;

    }

    /**
     * Indica si todos los ítems de una lista son verdaderos.
     *
     * @param items {@code List<ItemVerificado<T>>} ítems verificados
     * @param <T> tipo de la afirmación
     * @return {@code boolean} {@code true} si todos son verdaderos (o no hay ninguno)
     */
    private <T> boolean todosVerdaderos(List<ItemVerificado<T>> items) {

        return items.stream().allMatch(ItemVerificado::verdadero);

    }

    /**
     * Devuelve la lista, o una vacía si es {@code null}.
     *
     * @param lista {@code List<T>} lista posiblemente nula
     * @param <T> tipo de los elementos
     * @return {@code List<T>} la misma lista o una vacía
     */
    private <T> List<T> orEmpty(List<T> lista) {

        return lista != null ? lista : List.of();

    }

    /**
     * Compara dos textos sin mayúsculas ni tildes.
     *
     * @param texto {@code String} un texto
     * @param otroTexto {@code String} el otro texto
     * @return {@code boolean} {@code true} si son iguales una vez normalizados
     */
    private boolean sonIguales(String texto, String otroTexto) {

        return NormalizadorBusqueda.normalizarTexto(texto).equals(NormalizadorBusqueda.normalizarTexto(otroTexto));

    }

    /**
     * Indica si una indicación dicha por el agente corresponde a una indicación vigente: el texto
     * dicho está contenido en el nombre o la descripción de la real, o al revés.
     *
     * @param indicacionAfirmada {@code String} texto de la indicación que dijo el agente
     * @param indicacion {@code IndicacionPrestacion} indicación vigente de la prestación
     * @return {@code boolean} {@code true} si coinciden
     */
    private boolean coincideIndicacion(String indicacionAfirmada, IndicacionPrestacion indicacion) {

        String afirmada = NormalizadorBusqueda.normalizarTexto(indicacionAfirmada);
        String nombre = NormalizadorBusqueda.normalizarTexto(indicacion.getNombre());
        String descripcion = indicacion.getDescripcion() != null ? NormalizadorBusqueda.normalizarTexto(indicacion.getDescripcion()) : "";

        boolean coincide = !afirmada.isBlank() && (nombre.contains(afirmada) || descripcion.contains(afirmada)
                || afirmada.contains(nombre) || (!descripcion.isBlank() && afirmada.contains(descripcion)));
        return coincide;

    }

    /**
     * Busca una especialidad activa por su nombre exacto (sin mayúsculas ni tildes).
     *
     * @param nombre {@code String} nombre de la especialidad
     * @return {@code Optional<Especialidad>} la especialidad, o vacío si no existe
     */
    private Optional<Especialidad> findEspecialidadByNombre(String nombre) {

        Optional<Especialidad> especialidadExistente = especialidadDomainService.findEspecialidadesActivas().stream()
                .filter(especialidad -> sonIguales(especialidad.getNombre(), nombre))
                .findFirst();
        return especialidadExistente;

    }

    /**
     * Busca una prestación publicada por su nombre exacto (sin mayúsculas ni tildes).
     *
     * @param nombre {@code String} nombre de la prestación
     * @return {@code Optional<Prestacion>} la prestación publicada, o vacío si no existe o no está publicada
     */
    private Optional<Prestacion> findPrestacionPublicadaByNombre(String nombre) {

        Optional<Prestacion> prestacionPublicada = prestacionDomainService.searchPrestacionesByNombre(nombre, 1.0).resultados().stream()
                .filter(prestacion -> sonIguales(prestacion.getNombre(), nombre))
                .filter(prestacion -> historicoEstadoPrestacionDomainService.getEstadoVigente(prestacion.getId()) == EstadoPrestacion.PUBLICADA)
                .findFirst();
        return prestacionPublicada;

    }

    /**
     * Busca el horario libre de un médico para una prestación en una fecha y hora exactas.
     *
     * @param matricula {@code String} matrícula del médico
     * @param nombrePrestacion {@code String} nombre de la prestación
     * @param fecha {@code LocalDate} fecha del horario
     * @param hora {@code LocalTime} hora de inicio del horario
     * @return {@code Optional<ListHorarioDisponibleResponse>} el horario libre, o vacío si no hay
     */
    private Optional<ListHorarioDisponibleResponse> findSlotLibre(String matricula, String nombrePrestacion,
            LocalDate fecha, LocalTime hora) {

        Optional<Medico> medicoActivo = medicoDomainService.findOptionalMedicoActivoByMatricula(matricula);
        Optional<Prestacion> prestacionPublicada = findPrestacionPublicadaByNombre(nombrePrestacion);
        if (medicoActivo.isEmpty() || prestacionPublicada.isEmpty()) {
            return Optional.empty();
        }

        Optional<ListHorarioDisponibleResponse> slotLibre = findSlotsLibres(medicoActivo.get(), prestacionPublicada.get(), fecha, null).stream()
                .filter(slot -> slot.horaDesde().truncatedTo(ChronoUnit.MINUTES).equals(hora.truncatedTo(ChronoUnit.MINUTES)))
                .findFirst();
        return slotLibre;

    }

    /**
     * Lista los horarios libres (dentro del plazo y del horizonte de reserva) de una prestación,
     * opcionalmente de un médico, de un día y de una franja del día.
     *
     * @param medico {@code Medico} médico, o {@code null} para cualquiera
     * @param prestacion {@code Prestacion} prestación
     * @param fecha {@code LocalDate} fecha, o {@code null} para cualquier día
     * @param franjaHoraria {@code FranjaHorariaAgente} franja del día, o {@code null} para todo el día
     * @return {@code List<ListHorarioDisponibleResponse>} horarios libres (puede estar vacía)
     */
    private List<ListHorarioDisponibleResponse> findSlotsLibres(Medico medico, Prestacion prestacion, LocalDate fecha,
            FranjaHorariaAgente franjaHoraria) {

        AgendaHorariosCriteria agendaHorariosCriteria = new AgendaHorariosCriteria();
        UUIDFilter prestacionFilter = new UUIDFilter();
        prestacionFilter.setEquals(prestacion.getId());
        agendaHorariosCriteria.setPrestacionId(prestacionFilter);
        if (fecha != null) {
            LocalDateFilter fechaFilter = new LocalDateFilter();
            fechaFilter.setEquals(fecha);
            agendaHorariosCriteria.setFecha(fechaFilter);
        }
        if (franjaHoraria != null) {
            LocalTimeFilter horaDesdeFilter = new LocalTimeFilter();
            horaDesdeFilter.setGreaterThanOrEqual(franjaHoraria.desde());
            horaDesdeFilter.setLessThan(franjaHoraria.hasta());
            agendaHorariosCriteria.setHoraDesde(horaDesdeFilter);
        }
        if (medico != null) {
            UUIDFilter medicoFilter = new UUIDFilter();
            medicoFilter.setEquals(medico.getId());
            agendaHorariosCriteria.setMedicoId(medicoFilter);
        }

        List<ListHorarioDisponibleResponse> slotsLibres = agendaHorariosDiaQueryService.findHorariosDisponibles(
                agendaHorariosCriteria, PageRequest.of(0, TAMANIO_PAGINA_HORARIOS, Sort.by("fecha", "horaDesde"))).content();
        return slotsLibres;

    }

    /**
     * Arma el motivo del rechazo de una disponibilidad según lo que declaró el agente (fecha, franja
     * y/o hora), sin mencionar datos que no declaró.
     *
     * @param disponibilidadAfirmada {@code DisponibilidadAfirmada} afirmación rechazada
     * @param hayReal {@code boolean} si en la base hay horarios libres para esa afirmación
     * @return {@code String} el motivo, en español para el modelo
     */
    private String buildMotivoDisponibilidad(DisponibilidadAfirmada disponibilidadAfirmada, boolean hayReal) {

        String fechaTexto = disponibilidadAfirmada.fecha() != null ? FormatoMensaje.fecha(disponibilidadAfirmada.fecha()) : null;

        //Hora puntual
        if (disponibilidadAfirmada.hora() != null) {
            String fechaHoraTexto = fechaTexto + " a las " + disponibilidadAfirmada.hora().truncatedTo(ChronoUnit.MINUTES);
            String motivoHora = hayReal
                    ? "El " + fechaHoraTexto + " sí hay un horario libre. Consultalo antes de decir que no está."
                    : "El " + fechaHoraTexto + " no hay un horario libre.";
            return motivoHora;
        }

        //Día y/o franja
        String franjaTexto = disponibilidadAfirmada.franjaHoraria() == null ? "" : switch (disponibilidadAfirmada.franjaHoraria()) {
            case MANANA -> " a la mañana";
            case TARDE -> " a la tarde";
            case NOCHE -> " a la noche";
        };
        String contexto = (fechaTexto != null ? " el " + fechaTexto : "") + franjaTexto;
        String motivoDia = hayReal
                ? "Sí hay horarios libres" + contexto + ". Consultalos antes de decir que no hay."
                : "No hay horarios libres" + contexto + ".";
        return motivoDia;

    }

    //endregion

}
