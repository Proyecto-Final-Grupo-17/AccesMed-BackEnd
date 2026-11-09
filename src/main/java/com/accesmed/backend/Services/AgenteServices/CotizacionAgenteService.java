package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Domain.TipoCobertura;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPlanDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.Utils.ContextoCalculoMontoTurno;
import com.accesmed.backend.Services.Utils.EstrategiaCalcularMontoAPagarTurno;
import com.accesmed.backend.Services.Utils.FabricaEstrategiaCalcularMontoAPagarTurno;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cotización de turnos del canal chatbot (capa {@code Services/AgenteServices},
 * {@code Docs/Diseno-Chatbot.md §4.4}): calcula con qué cobertura y por cuánto queda un turno sin
 * guardar nada, con el mismo cálculo que la reserva (estrategia por {@code ModalidadCobertura}).
 * <p>
 * El paciente elige particular u obra social: si pide obra social y no cubre, se informa el motivo
 * y el precio particular, en vez de pasar a particular en silencio. La usan solo {@code AgenteApp}
 * (cotizar, reservar, reprogramar) y la verificación de respuestas del agente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CotizacionAgenteService {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    private final HistoricoEstadoPlanDomainService historicoEstadoPlanDomainService;
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

    //endregion

    //region ========== Métodos auxiliares privados ==========

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
