package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.AsignarCoberturaAnidadaRequest;
import com.accesmed.backend.Records.Plan.Request.DeshabilitarPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetCoberturaAnidadaResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPlanDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ObraSocialPlanPrestacionMapper;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Plan. Orquesta el flujo completo de los endpoints (agregar a una obra
 * social existente, actualización, transiciones de estado) validando reglas de negocio y
 * coordinando los services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanApp {

    //region ========== Dependencias ==========

    //Domain Services
    private final PlanDomainService planDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final HistoricoEstadoPlanDomainService historicoEstadoPlanDomainService;
    private final TurnoDomainService turnoDomainService;
    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    private final PrestacionDomainService prestacionDomainService;

    //Mappers
    private final PlanMapper planMapper;
    private final ObraSocialPlanPrestacionMapper obraSocialPlanPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Agrega un plan nuevo a una obra social activa existente, opcionalmente junto con
     * las coberturas de prestaciones iniciales, en una única transacción atómica. Nace en
     * estado {@code NO_PUBLICADO}.
     *
     * @param addPlanRequest {@code AddPlanRequest} datos del plan a agregar y, opcionalmente,
     *        sus coberturas
     * @return {@code GetPlanResponse} el plan agregado, con sus coberturas (si se enviaron)
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social o
     *         alguna prestación no existen (activas)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre ya existen en la obra social
     */
    @Transactional
    public GetPlanResponse createPlan(AddPlanRequest addPlanRequest) {

        log.info("Alta de plan iniciada: obraSocialId={}, código={}", addPlanRequest.obraSocialId(), addPlanRequest.codigo());

        //Validar que la obra social exista
        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(addPlanRequest.obraSocialId());

        //Validar que el código y el nombre sean únicos en la obra social
        planDomainService.validateCodigoPlanIsUnique(obraSocialExistente.getId(), addPlanRequest.codigo());
        planDomainService.validateNombrePlanIsUnique(obraSocialExistente.getId(), addPlanRequest.nombre());

        //Mapear y setear la obra social
        Plan planNuevo = planMapper.toEntity(addPlanRequest);
        planNuevo.setObraSocial(obraSocialExistente);

        //Guardar Plan
        Plan planGuardado = planDomainService.savePlan(planNuevo);

        //Abrir tramo inicial de histórico de estado (nace en NO_PUBLICADO)
        historicoEstadoPlanDomainService.openHistoricoInicialPlan(planGuardado);

        //Asignar cada cobertura anidada
        List<GetCoberturaAnidadaResponse> coberturasResponse = asignarCoberturasAnidadas(planGuardado, addPlanRequest.coberturas());

        //Devolver response mapeado. Recién abierto el tramo inicial, el estado vigente es NO_PUBLICADO.
        GetPlanResponse getPlanResponse = planMapper.toGetResponse(planGuardado, EstadoPlan.NO_PUBLICADO, coberturasResponse, null);
        return getPlanResponse;

    }

    /**
     * Actualiza código y nombre de un plan existente. Se puede modificar en cualquier
     * estado salvo {@code DESHABILITADO} (terminal e irreversible).
     *
     * @param updatePlanRequest {@code UpdatePlanRequest} datos a actualizar, incluyendo el id
     *        del plan (ya validado contra la ruta en el Controller)
     * @return {@code GetPlanResponse} el plan actualizado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan no existe o está deshabilitado
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el nombre es duplicado
     */
    @Transactional
    public GetPlanResponse updatePlan(UpdatePlanRequest updatePlanRequest) {

        UUID id = updatePlanRequest.id();

        log.info("Actualización de plan iniciada: id={}", id);

        //Buscar el plan activo (deshabilitado es terminal: no admite más cambios)
        Plan planExistente = planDomainService.findPlanActivoById(id);

        //Validar unicidad de los campos que vinieron
        if (updatePlanRequest.codigo() != null) {
            planDomainService.validateCodigoPlanIsUnique(planExistente.getObraSocial().getId(), updatePlanRequest.codigo());
        }
        if (updatePlanRequest.nombre() != null) {
            planDomainService.validateNombrePlanIsUnique(planExistente.getObraSocial().getId(), updatePlanRequest.nombre(), id);
        }

        //Aplicar los cambios y guardar
        planMapper.updatePlan(planExistente, updatePlanRequest);
        Plan planActualizado = planDomainService.savePlan(planExistente);

        //Calcular el estado vigente del histórico para el response
        EstadoPlan estadoVigente = historicoEstadoPlanDomainService.getEstadoVigente(id);

        //Mapear las coberturas activas del plan para el response
        List<GetCoberturaAnidadaResponse> coberturasResponse = obraSocialPlanPrestacionMapper.toGetCoberturaAnidadaResponses(
                obraSocialPlanPrestacionDomainService.findCoberturasActivasByPlan(id));

        //Devolver response mapeado
        GetPlanResponse getPlanResponse = planMapper.toGetResponse(planActualizado, estadoVigente, coberturasResponse, null);
        return getPlanResponse;

    }

    /**
     * Publica un plan (transición reversible {@code NO_PUBLICADO -> PUBLICADO}).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code CambioEstadoPlanResponse} el plan publicado
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plan no existe,
     *         ya está deshabilitado, o no se puede publicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPlanResponse publishPlan(UUID id) {

        log.info("Publicación de plan iniciada: id={}", id);

        //Transicionar el estado a PUBLICADO
        Plan planPublicado = historicoEstadoPlanDomainService.changeEstadoPlan(id, EstadoPlan.PUBLICADO, null);

        //Devolver response mapeado (el estado vigente tras la transición es PUBLICADO)
        CambioEstadoPlanResponse cambioEstadoPlanResponse = planMapper.toCambioEstadoResponse(planPublicado, EstadoPlan.PUBLICADO);
        return cambioEstadoPlanResponse;

    }

    /**
     * Despublica un plan (transición reversible {@code PUBLICADO -> NO_PUBLICADO}).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code CambioEstadoPlanResponse} el plan despublicado
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plan no existe,
     *         ya está deshabilitado, o no se puede despublicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPlanResponse unpublishPlan(UUID id) {

        log.info("Despublicación de plan iniciada: id={}", id);

        //Transicionar el estado a NO_PUBLICADO
        Plan planDespublicado = historicoEstadoPlanDomainService.changeEstadoPlan(id, EstadoPlan.NO_PUBLICADO, null);

        //Devolver response mapeado (el estado vigente tras la transición es NO_PUBLICADO)
        CambioEstadoPlanResponse cambioEstadoPlanResponse = planMapper.toCambioEstadoResponse(planDespublicado, EstadoPlan.NO_PUBLICADO);
        return cambioEstadoPlanResponse;

    }

    /**
     * Deshabilita un plan (transición terminal e irreversible). Restrictiva: rechaza si
     * hay turnos vivos cubiertos por el plan. No rige la regla del "último plan no
     * deshabilitado de una obra social activa" (eliminada en v3).
     *
     * @param deshabilitarPlanRequest {@code DeshabilitarPlanRequest} motivo opcional, incluyendo
     *        el id del plan (ya validado contra la ruta en el Controller)
     * @return {@code CambioEstadoPlanResponse} el plan deshabilitado
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plan no existe,
     *         ya está deshabilitado, o tiene turnos vivos cubiertos
     */
    @Transactional
    public CambioEstadoPlanResponse disablePlan(DeshabilitarPlanRequest deshabilitarPlanRequest) {

        UUID id = deshabilitarPlanRequest.id();

        log.info("Deshabilitación de plan iniciada: id={}", id);

        //Validar que no tenga turnos vivos cubiertos
        turnoDomainService.validateSinTurnosVivosDePlan(id);

        //Dar de baja las ObraSocialPaciente que referencian el plan (A5, paso 2)
        obraSocialPacienteDomainService.softDeleteByPlan(id, "Baja de plan");

        //Dar de baja las coberturas de prestaciones del plan (A5, paso 1)
        obraSocialPlanPrestacionDomainService.softDeleteByPlan(id, "Baja de plan");

        //Transicionar el estado a DESHABILITADO
        Plan planDeshabilitado = historicoEstadoPlanDomainService.changeEstadoPlan(
                id, EstadoPlan.DESHABILITADO, deshabilitarPlanRequest.motivo());

        //Devolver response mapeado (el estado vigente tras la transición es DESHABILITADO)
        CambioEstadoPlanResponse cambioEstadoPlanResponse = planMapper.toCambioEstadoResponse(planDeshabilitado, EstadoPlan.DESHABILITADO);
        return cambioEstadoPlanResponse;

    }

    /**
     * Asigna al plan cada cobertura anidada del request, si se enviaron. Validando la
     * existencia de la prestación y la coherencia de la cobertura, en el mismo orden que
     * {@code ObraSocialPrestacionApp.assignPrestacion}.
     *
     * @param plan {@code Plan} plan ya guardado al que se le asignan las coberturas
     * @param coberturasRequest {@code List<AsignarCoberturaAnidadaRequest>} coberturas a asignar,
     *        o {@code null} si no se enviaron
     * @return {@code List<GetCoberturaAnidadaResponse>} las coberturas asignadas, mapeadas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si alguna prestación
     *         no existe (activa)
     */
    private List<GetCoberturaAnidadaResponse> asignarCoberturasAnidadas(Plan plan, List<AsignarCoberturaAnidadaRequest> coberturasRequest) {

        List<GetCoberturaAnidadaResponse> coberturasResponse = new ArrayList<>();

        if (coberturasRequest == null) {
            return coberturasResponse;
        }

        for (AsignarCoberturaAnidadaRequest coberturaAnidada : coberturasRequest) {
            //La coherencia de la modalidad ya la valida @CoherenciaCobertura en el Controller
            Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaById(coberturaAnidada.prestacionId());

            ObraSocialPlanPrestacion coberturaNueva = obraSocialPlanPrestacionMapper.toEntity(coberturaAnidada);
            coberturaNueva.setPlan(plan);
            coberturaNueva.setPrestacion(prestacionExistente);

            ObraSocialPlanPrestacion coberturaGuardada = obraSocialPlanPrestacionDomainService.saveObraSocialPlanPrestacion(coberturaNueva);
            coberturasResponse.add(obraSocialPlanPrestacionMapper.toGetCoberturaAnidadaResponse(coberturaGuardada));
        }

        return coberturasResponse;

    }

    //endregion

}
