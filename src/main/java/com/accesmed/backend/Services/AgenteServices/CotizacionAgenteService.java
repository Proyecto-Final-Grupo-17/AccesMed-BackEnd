package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoCobertura;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Records.Agente.Response.GetCoberturaPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetCoberturaPrestacionAgenteResponse.CoberturaPlan;
import com.accesmed.backend.Records.Agente.Response.GetCoberturaPrestacionAgenteResponse.MontoMedico;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPlanDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.Utils.ContextoCalculoMontoTurno;
import com.accesmed.backend.Services.Utils.EstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.FabricaEstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.NormalizadorBusqueda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cotización de turnos del canal chatbot (capa {@code Services/AgenteServices},
 * {@code Docs/Diseno-Chatbot.md §4.4}): calcula con qué cobertura y por cuánto queda un turno sin
 * guardar nada, con el mismo cálculo que la reserva (estrategia por {@code ModalidadCobertura}).
 * <p>
 * El paciente elige particular u obra social: si pide obra social y no cubre, se informa el motivo
 * y el precio particular, en vez de pasar a particular en silencio. La usan solo {@code AgenteApp}
 * (cotizar, reservar, reprogramar, consultar la cobertura de una prestación) y la verificación de
 * respuestas del agente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CotizacionAgenteService {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    private final HistoricoEstadoPlanDomainService historicoEstadoPlanDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanDomainService planDomainService;
    private final FabricaEstrategiaCalcularMontoAPagarTurno fabricaEstrategiaCalcularMontoAPagarTurno;

    //endregion

    //region ========== Métodos ==========

    /**
     * Cotiza un turno nuevo con la cobertura que pidió el paciente. Con obra social toma la primera
     * cobertura activa del paciente cuyo plan está publicado y cubre la prestación.
     *
     * @param paciente {@code Paciente} paciente activo
     * @param prestacion {@code Prestacion} prestación del turno
     * @param medicoPrestacion {@code MedicoPrestacion} asignación vigente del médico en la fecha del turno (da el precio particular)
     * @param tipoCobertura {@code TipoCobertura} cobertura pedida
     * @return {@code CotizacionTurnoAgente} cobertura, monto y, si no cubre, el motivo
     */
    public CotizacionTurnoAgente cotizarTurno(Paciente paciente, Prestacion prestacion,
            MedicoPrestacion medicoPrestacion, TipoCobertura tipoCobertura) {

        BigDecimal precioParticular = medicoPrestacion.getPrecioParticular();

        //Particular: siempre aplica
        if (tipoCobertura == TipoCobertura.PARTICULAR) {
            CotizacionTurnoAgente cotizacionParticular = new CotizacionTurnoAgente(TipoCobertura.PARTICULAR, null,
                    true, null, null, precioParticular, precioParticular);
            return cotizacionParticular;
        }

        //Obra social: el paciente tiene que tener una cargada
        List<ObraSocialPaciente> coberturasActivas = obraSocialPacienteDomainService.findCoberturasActivasByPaciente(paciente.getId());
        if (coberturasActivas.isEmpty()) {
            CotizacionTurnoAgente cotizacionSinObraSocial = buildCotizacionNoCubre("PACIENTE_SIN_OBRA_SOCIAL",
                    "No tenés una obra social cargada. Podés cargarla en tus datos o sacar el turno como particular.",
                    precioParticular);
            return cotizacionSinObraSocial;
        }

        //Primera cobertura con plan publicado que cubre la prestación
        boolean algunPlanPublicado = false;
        for (ObraSocialPaciente coberturaActiva : coberturasActivas) {
            if (!historicoEstadoPlanDomainService.isPlanPublicado(coberturaActiva.getPlan().getId())) {
                continue;
            }
            algunPlanPublicado = true;
            if (obraSocialPlanPrestacionDomainService.existsCoberturaActivaByPlanAndPrestacion(
                    coberturaActiva.getPlan().getId(), prestacion.getId())) {
                CotizacionTurnoAgente cotizacionObraSocial = new CotizacionTurnoAgente(TipoCobertura.OBRA_SOCIAL,
                        coberturaActiva, true, null, null,
                        calcularMontoObraSocial(coberturaActiva, prestacion, precioParticular), precioParticular);
                return cotizacionObraSocial;
            }
        }

        //Ninguna cubre: decir por qué
        ObraSocialPaciente coberturaPrincipal = coberturasActivas.getFirst();
        String nombrePlan = coberturaPrincipal.getPlan().getNombre();
        CotizacionTurnoAgente cotizacionNoCubre = algunPlanPublicado
                ? buildCotizacionNoCubre("PLAN_NO_CUBRE_PRESTACION",
                        "Tu plan " + nombrePlan + " no cubre " + prestacion.getNombre() + ".", precioParticular)
                : buildCotizacionNoCubre("PLAN_NO_PUBLICADO",
                        "Tu plan " + nombrePlan + " no está habilitado en la clínica en este momento.", precioParticular);
        log.debug("Cotización sin cobertura: paciente={}, prestación={}, motivo={}",
                paciente.getId(), prestacion.getId(), cotizacionNoCubre.codigoMotivo());
        return cotizacionNoCubre;

    }

    /**
     * Cotiza la reprogramación de un turno: conserva la cobertura del turno original y recalcula el
     * monto con el precio vigente en la nueva fecha (mismo criterio que la reprogramación).
     *
     * @param turnoOriginal {@code Turno} turno a reprogramar
     * @param medicoPrestacion {@code MedicoPrestacion} asignación vigente del médico en la nueva fecha
     * @return {@code CotizacionTurnoAgente} cobertura del turno original y monto recalculado
     */
    public CotizacionTurnoAgente cotizarReprogramacion(Turno turnoOriginal, MedicoPrestacion medicoPrestacion) {

        BigDecimal precioParticular = medicoPrestacion.getPrecioParticular();
        ObraSocialPaciente coberturaOriginal = turnoOriginal.getObraSocialPaciente();

        //Turno particular: sigue particular
        if (coberturaOriginal == null) {
            CotizacionTurnoAgente cotizacionParticular = new CotizacionTurnoAgente(TipoCobertura.PARTICULAR, null,
                    true, null, null, precioParticular, precioParticular);
            return cotizacionParticular;
        }

        //Con obra social: el plan tiene que seguir publicado y cubriendo la prestación
        Prestacion prestacion = turnoOriginal.getPrestacion();
        String nombrePlan = coberturaOriginal.getPlan().getNombre();
        if (!historicoEstadoPlanDomainService.isPlanPublicado(coberturaOriginal.getPlan().getId())) {
            CotizacionTurnoAgente cotizacionPlanNoPublicado = buildCotizacionNoCubre("PLAN_NO_PUBLICADO",
                    "El plan " + nombrePlan + " del turno ya no está habilitado: no se puede reprogramar.", precioParticular);
            return cotizacionPlanNoPublicado;
        }
        if (!obraSocialPlanPrestacionDomainService.existsCoberturaActivaByPlanAndPrestacion(
                coberturaOriginal.getPlan().getId(), prestacion.getId())) {
            CotizacionTurnoAgente cotizacionNoCubre = buildCotizacionNoCubre("PLAN_NO_CUBRE_PRESTACION",
                    "El plan " + nombrePlan + " ya no cubre " + prestacion.getNombre() + ": no se puede reprogramar.",
                    precioParticular);
            return cotizacionNoCubre;
        }

        CotizacionTurnoAgente cotizacionObraSocial = new CotizacionTurnoAgente(TipoCobertura.OBRA_SOCIAL,
                coberturaOriginal, true, null, null,
                calcularMontoObraSocial(coberturaOriginal, prestacion, precioParticular), precioParticular);
        return cotizacionObraSocial;

    }

    /**
     * Resuelve por nombre los planes de una consulta de cobertura: con obra social y plan, ese plan; con
     * solo la obra social, todos sus planes vigentes; con solo el plan, los planes vigentes de ese nombre en
     * cualquier obra social. El nombre se compara sin mayúsculas ni tildes, igual o contenido. Si no existe,
     * devuelve el motivo en lugar de lanzar una excepción: "no trabajamos con esa obra social" es una respuesta.
     *
     * @param nombreObraSocial {@code String} obra social dicha por el paciente, o {@code null}
     * @param nombrePlan {@code String} plan dicho por el paciente, o {@code null}
     * @return {@code PlanesResueltosAgente} los planes encontrados, o el motivo por el que no hay
     */
    public PlanesResueltosAgente findPlanesByNombres(String nombreObraSocial, String nombrePlan) {

        boolean conObraSocial = nombreObraSocial != null && !nombreObraSocial.isBlank();
        boolean conPlan = nombrePlan != null && !nombrePlan.isBlank();

        //Obras sociales activas candidatas
        List<ObraSocial> obrasSocialesCandidatas = obraSocialDomainService.findObrasSocialesActivas().stream()
                .filter(obraSocial -> !conObraSocial || coincideNombre(obraSocial.getNombre(), nombreObraSocial))
                .toList();
        if (conObraSocial && obrasSocialesCandidatas.isEmpty()) {
            PlanesResueltosAgente obraSocialNoEncontrada = new PlanesResueltosAgente(List.of(), nombreObraSocial, nombrePlan,
                    "OBRA_SOCIAL_NO_ENCONTRADA", "No trabajamos con la obra social \"" + nombreObraSocial + "\".");
            return obraSocialNoEncontrada;
        }

        //Sus planes vigentes (no deshabilitados), filtrados por el plan dicho
        List<Plan> planesDeLasObrasSociales = obrasSocialesCandidatas.stream()
                .flatMap(obraSocial -> planDomainService.findPlanesNoDeshabilitadosByObraSocial(obraSocial.getId()).stream())
                .toList();
        List<Plan> planesEncontrados = planesDeLasObrasSociales.stream()
                .filter(plan -> !conPlan || coincideNombre(plan.getNombre(), nombrePlan))
                .toList();
        if (planesEncontrados.isEmpty()) {
            String motivo = conPlan
                    ? "No encontramos el plan \"" + nombrePlan + "\"" + (conObraSocial ? " en " + nombreObraSocial : "")
                            + (planesDeLasObrasSociales.isEmpty() ? "." : ". Planes disponibles: "
                            + planesDeLasObrasSociales.stream().map(Plan::getNombre).collect(Collectors.joining(", ")) + ".")
                    : "No encontramos planes vigentes de \"" + nombreObraSocial + "\".";
            PlanesResueltosAgente planNoEncontrado = new PlanesResueltosAgente(List.of(), nombreObraSocial, nombrePlan,
                    "PLAN_NO_ENCONTRADO", motivo);
            return planNoEncontrado;
        }

        PlanesResueltosAgente planesResueltos = new PlanesResueltosAgente(planesEncontrados, nombreObraSocial, nombrePlan, null, null);
        return planesResueltos;

    }

    /**
     * Indica si un plan cubre hoy una prestación: el plan tiene que estar publicado y tener una cobertura
     * activa para la prestación (sin fila de cobertura, no la cubre).
     *
     * @param plan {@code Plan} plan a consultar
     * @param prestacion {@code Prestacion} prestación
     * @return {@code boolean} {@code true} si el plan cubre la prestación
     */
    public boolean isPrestacionCubierta(Plan plan, Prestacion prestacion) {

        boolean prestacionCubierta = historicoEstadoPlanDomainService.isPlanPublicado(plan.getId())
                && obraSocialPlanPrestacionDomainService.existsCoberturaActivaByPlanAndPrestacion(plan.getId(), prestacion.getId());
        return prestacionCubierta;

    }

    /**
     * Arma la cobertura de una prestación para los planes consultados, sin elegir un horario: por cada plan
     * dice si cubre (plan publicado y cobertura vigente), con qué modalidad y por qué no cubre, y cuánto
     * pagaría el paciente con cada médico que hace la prestación (el precio particular es del médico). Calcula
     * el monto con la misma estrategia por modalidad que la cotización de un turno. Las coberturas de todos los
     * planes se leen juntas (sin N+1).
     *
     * @param prestacion {@code Prestacion} prestación publicada consultada
     * @param planesResueltos {@code PlanesResueltosAgente} planes consultados, o el motivo por el que no existen
     * @param asignaciones {@code List<MedicoPrestacion>} asignaciones vigentes de los médicos que hacen la prestación (da el precio particular)
     * @return {@code GetCoberturaPrestacionAgenteResponse} una cobertura por plan consultado
     */
    public GetCoberturaPrestacionAgenteResponse getCoberturaPrestacion(Prestacion prestacion,
            PlanesResueltosAgente planesResueltos, List<MedicoPrestacion> asignaciones) {

        //Sin planes (obra social o plan inexistentes): solo se informa el motivo y el precio particular
        if (planesResueltos.planes().isEmpty()) {
            CoberturaPlan coberturaSinPlan = new CoberturaPlan(planesResueltos.obraSocialDicha(), planesResueltos.planDicho(),
                    false, null, null, null, planesResueltos.codigoMotivo(), planesResueltos.motivo(),
                    buildMontosMedicos(asignaciones, null));
            return new GetCoberturaPrestacionAgenteResponse(prestacion.getNombre(), List.of(coberturaSinPlan));
        }

        //Estados de los planes y coberturas de la prestación, en bloque
        List<UUID> planIds = planesResueltos.planes().stream().map(Plan::getId).toList();
        Map<UUID, EstadoPlan> estadosPlanes = historicoEstadoPlanDomainService.getEstadosVigentes(planIds);
        Map<UUID, ObraSocialPlanPrestacion> coberturasPorPlan = obraSocialPlanPrestacionDomainService
                .findCoberturasActivasByPlanes(planIds).stream()
                .filter(cobertura -> cobertura.getPrestacion().getId().equals(prestacion.getId()))
                .collect(Collectors.toMap(cobertura -> cobertura.getPlan().getId(), Function.identity(), (primera, segunda) -> primera));

        List<CoberturaPlan> coberturasPlanes = planesResueltos.planes().stream()
                .map(plan -> {
                    String nombreObraSocial = plan.getObraSocial().getNombre();
                    if (estadosPlanes.get(plan.getId()) != EstadoPlan.PUBLICADO) {
                        return new CoberturaPlan(nombreObraSocial, plan.getNombre(), false, null, null, null,
                                "PLAN_NO_PUBLICADO", "El plan " + plan.getNombre() + " no está habilitado en la clínica en este momento.",
                                buildMontosMedicos(asignaciones, null));
                    }
                    ObraSocialPlanPrestacion cobertura = coberturasPorPlan.get(plan.getId());
                    if (cobertura == null) {
                        return new CoberturaPlan(nombreObraSocial, plan.getNombre(), false, null, null, null,
                                "PLAN_NO_CUBRE_PRESTACION", "El plan " + plan.getNombre() + " no cubre " + prestacion.getNombre() + ".",
                                buildMontosMedicos(asignaciones, null));
                    }
                    return new CoberturaPlan(nombreObraSocial, plan.getNombre(), true, cobertura.getModalidadCobertura(),
                            cobertura.getPorcentajeCobertura(), cobertura.getCoseguro(), null, null,
                            buildMontosMedicos(asignaciones, cobertura));
                })
                .toList();

        GetCoberturaPrestacionAgenteResponse getCoberturaPrestacionAgenteResponse =
                new GetCoberturaPrestacionAgenteResponse(prestacion.getNombre(), coberturasPlanes);
        return getCoberturaPrestacionAgenteResponse;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Arma el monto de la prestación con cada médico: con cobertura, el cálculo de su modalidad sobre el
     * precio particular del médico; sin cobertura, el precio particular.
     *
     * @param asignaciones {@code List<MedicoPrestacion>} asignaciones vigentes de los médicos que hacen la prestación
     * @param cobertura {@code ObraSocialPlanPrestacion} cobertura del plan, o {@code null} si no cubre
     * @return {@code List<MontoMedico>} un monto por médico, ordenado por apellido y nombre
     */
    private List<MontoMedico> buildMontosMedicos(List<MedicoPrestacion> asignaciones, ObraSocialPlanPrestacion cobertura) {

        List<MontoMedico> montosMedicos = asignaciones.stream()
                .sorted(Comparator.comparing((MedicoPrestacion asignacion) -> asignacion.getMedico().getApellido())
                        .thenComparing(asignacion -> asignacion.getMedico().getNombre()))
                .map(asignacion -> {
                    BigDecimal precioParticular = asignacion.getPrecioParticular();
                    BigDecimal montoAPagar = cobertura == null ? precioParticular
                            : fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(cobertura.getModalidadCobertura())
                                    .calcularMonto(new ContextoCalculoMontoTurno(precioParticular,
                                            cobertura.getPorcentajeCobertura(), cobertura.getCoseguro()));
                    return new MontoMedico(asignacion.getMedico().getNombre(), asignacion.getMedico().getApellido(),
                            asignacion.getMedico().getMatricula(), precioParticular, montoAPagar);
                })
                .toList();
        return montosMedicos;

    }

    /**
     * Compara un nombre real con lo que escribió el paciente, sin mayúsculas ni tildes: igual, o uno
     * contenido en el otro ("OSDE" con "OSDE 210").
     *
     * @param nombreReal {@code String} nombre de la obra social o del plan
     * @param nombreDicho {@code String} lo que escribió el paciente
     * @return {@code boolean} {@code true} si coinciden
     */
    private boolean coincideNombre(String nombreReal, String nombreDicho) {

        String real = NormalizadorBusqueda.normalizarTexto(nombreReal);
        String dicho = NormalizadorBusqueda.normalizarTexto(nombreDicho);
        boolean coincide = !dicho.isBlank() && (real.equals(dicho) || real.contains(dicho) || dicho.contains(real));
        return coincide;

    }

    /**
     * Calcula lo que paga el paciente con una cobertura que cubre la prestación.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} cobertura del paciente
     * @param prestacion {@code Prestacion} prestación del turno
     * @param precioParticular {@code BigDecimal} precio particular del médico
     * @return {@code BigDecimal} monto a pagar según la modalidad de cobertura
     */
    private BigDecimal calcularMontoObraSocial(ObraSocialPaciente obraSocialPaciente, Prestacion prestacion,
            BigDecimal precioParticular) {

        ObraSocialPlanPrestacion coberturaExistente = obraSocialPlanPrestacionDomainService
                .findCoberturaByPlanAndPrestacion(obraSocialPaciente.getPlan().getId(), prestacion.getId());

        EstrategiaCalcularMontoAPagarTurno estrategia =
                fabricaEstrategiaCalcularMontoAPagarTurno.obtenerEstrategia(coberturaExistente.getModalidadCobertura());
        BigDecimal montoAPagar = estrategia.calcularMonto(new ContextoCalculoMontoTurno(
                precioParticular, coberturaExistente.getPorcentajeCobertura(), coberturaExistente.getCoseguro()));
        return montoAPagar;

    }

    /**
     * Arma una cotización de obra social que no cubre: informa el motivo y el precio particular.
     *
     * @param codigoMotivo {@code String} código del motivo
     * @param motivo {@code String} motivo en español para el paciente
     * @param precioParticular {@code BigDecimal} precio particular del médico
     * @return {@code CotizacionTurnoAgente} cotización con {@code cubre = false}
     */
    private CotizacionTurnoAgente buildCotizacionNoCubre(String codigoMotivo, String motivo, BigDecimal precioParticular) {

        CotizacionTurnoAgente cotizacionNoCubre = new CotizacionTurnoAgente(TipoCobertura.OBRA_SOCIAL, null,
                false, codigoMotivo, motivo, precioParticular, precioParticular);
        return cotizacionNoCubre;

    }

    //endregion

}
