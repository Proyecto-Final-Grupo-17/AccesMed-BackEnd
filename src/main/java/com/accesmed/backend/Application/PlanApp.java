package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.Plan.Criteria.PlanCriteria;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.DeshabilitarPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPlanDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.PlanQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
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

    //Mappers
    private final PlanMapper planMapper;

    //Query Services
    private final PlanQueryService planQueryService;
    //endregion

    //region ========== Métodos ==========

    /**
     * Agrega un plan nuevo a una obra social activa existente. Nace en estado
     * {@code NO_PUBLICADO}.
     *
     * @param addPlanRequest {@code AddPlanRequest} datos del plan a agregar
     * @return {@code GetPlanResponse} el plan agregado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social no existe
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

        //Devolver response mapeado. Recién abierto el tramo inicial, el estado vigente es NO_PUBLICADO.
        GetPlanResponse getPlanResponse = planMapper.toGetResponse(planGuardado, EstadoPlan.NO_PUBLICADO);
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

        //Devolver response mapeado
        GetPlanResponse getPlanResponse = planMapper.toGetResponse(planActualizado, estadoVigente);
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

        //TODO (A5, paso 1): dar de baja las ObraSocialPlanPrestacion del plan. Sin módulo
        // (repo/service/App/controller) al que delegarlo todavía. Ver
        // Docs/Planes/auditoria-v3-y-feature-agenda.md.

        //Transicionar el estado a DESHABILITADO
        Plan planDeshabilitado = historicoEstadoPlanDomainService.changeEstadoPlan(
                id, EstadoPlan.DESHABILITADO, deshabilitarPlanRequest.motivo());

        //Devolver response mapeado (el estado vigente tras la transición es DESHABILITADO)
        CambioEstadoPlanResponse cambioEstadoPlanResponse = planMapper.toCambioEstadoResponse(planDeshabilitado, EstadoPlan.DESHABILITADO);
        return cambioEstadoPlanResponse;

    }

    /**
     * Busca el plan que cumple el criteria de filtrado dinámico proporcionado. A
     * diferencia de {@link #findPlanes}, devuelve un único plan (no paginado) — pensado
     * para criterios que identifican un plan puntual (ej. {@code id.equals}).
     *
     * @param planCriteria {@code PlanCriteria} filtros a aplicar
     * @return {@code GetPlanResponse} el plan encontrado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún plan cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetPlanResponse findPlanByCriteria(PlanCriteria planCriteria) {

        log.info("Búsqueda de plan iniciada: criteria={}", planCriteria);

        Plan planExistente = planQueryService.findPlanByCriteria(planCriteria);

        //Calcular el estado vigente del histórico para el response
        EstadoPlan estadoVigente = historicoEstadoPlanDomainService.getEstadoVigente(planExistente.getId());

        GetPlanResponse getPlanResponse = planMapper.toGetResponse(planExistente, estadoVigente);
        return getPlanResponse;

    }

    /**
     * Lista planes según el criteria de filtrado dinámico proporcionado.
     *
     * @param planCriteria {@code PlanCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListPlanResponse>} página de planes que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListPlanResponse> findPlanes(PlanCriteria planCriteria, Pageable pageable) {

        log.info("Listado de planes iniciado: criteria={}, page={}", planCriteria, pageable);

        //Buscar planes que cumplen el criteria, paginados
        Page<Plan> planesPagina = planQueryService.findByCriteria(planCriteria, pageable);

        //Cargar el estado vigente de toda la página en una sola consulta (evita N+1)
        Map<UUID, EstadoPlan> estadosVigentes = historicoEstadoPlanDomainService.getEstadosVigentes(
                planesPagina.getContent().stream().map(Plan::getId).toList());

        //Devolver response mapeado, alimentando el estado de cada fila desde el mapa
        PageResponse<ListPlanResponse> pageResponse = PageResponse.from(planesPagina,
                plan -> planMapper.toListResponse(plan, estadosVigentes.get(plan.getId())));
        return pageResponse;

    }

    //endregion

}
