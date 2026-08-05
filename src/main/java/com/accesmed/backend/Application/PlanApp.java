package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.DeshabilitarPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import com.accesmed.backend.Services.QueryServices.PlanQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    //region ========== Dependencias o inyecciones ==========

    private final PlanDomainService planDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanQueryService planQueryService;
    private final PlanMapper planMapper;

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
    public GetPlanResponse addPlan(AddPlanRequest addPlanRequest) {

        log.info("Alta de plan iniciada: obraSocialId={}, código={}", addPlanRequest.obraSocialId(), addPlanRequest.codigo());

        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(addPlanRequest.obraSocialId());

        planDomainService.validateCodigoPlanIsUnique(obraSocialExistente.getId(), addPlanRequest.codigo());
        planDomainService.validateNombrePlanIsUnique(obraSocialExistente.getId(), addPlanRequest.nombre());

        Plan planNuevo = planMapper.toEntity(addPlanRequest);
        planNuevo.setObraSocial(obraSocialExistente);

        Plan planGuardado = planDomainService.savePlan(planNuevo);
        planDomainService.abrirTramoInicial(planGuardado);

        return planMapper.toGetResponse(planGuardado);

    }

    /**
     * Actualiza código y nombre de un plan existente.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updatePlanRequest {@code UpdatePlanRequest} datos a actualizar
     * @return {@code GetPlanResponse} el plan actualizado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el nombre es duplicado
     */
    @Transactional
    public GetPlanResponse updatePlan(UUID id, UpdatePlanRequest updatePlanRequest) {

        log.info("Actualización de plan iniciada: id={}", id);

        Plan planExistente = planDomainService.findPlanById(id);

        if (updatePlanRequest.codigo() != null) {
            planDomainService.validateCodigoPlanIsUnique(planExistente.getObraSocial().getId(), updatePlanRequest.codigo());
        }
        if (updatePlanRequest.nombre() != null) {
            planDomainService.validateNombrePlanIsUnique(planExistente.getObraSocial().getId(), updatePlanRequest.nombre(), id);
        }

        planMapper.updatePlan(planExistente, updatePlanRequest);

        Plan planActualizado = planDomainService.savePlan(planExistente);

        return planMapper.toGetResponse(planActualizado);

    }

    /**
     * Publica un plan (transición reversible {@code NO_PUBLICADO -> PUBLICADO}).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code CambioEstadoPlanResponse} el plan publicado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no se puede publicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPlanResponse publicarPlan(UUID id) {

        log.info("Publicación de plan iniciada: id={}", id);

        Plan planExistente = planDomainService.findPlanById(id);

        planDomainService.validatePuedePublicar(planExistente);

        Plan planPublicado = planDomainService.abrirTramoEstado(planExistente, EstadoPlan.PUBLICADO, null);

        return planMapper.toCambioEstadoResponse(planPublicado);

    }

    /**
     * Despublica un plan (transición reversible {@code PUBLICADO -> NO_PUBLICADO}).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code CambioEstadoPlanResponse} el plan despublicado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no se puede despublicar desde el estado actual
     */
    @Transactional
    public CambioEstadoPlanResponse despublicarPlan(UUID id) {

        log.info("Despublicación de plan iniciada: id={}", id);

        Plan planExistente = planDomainService.findPlanById(id);

        planDomainService.validatePuedeDespublicar(planExistente);

        Plan planDespublicado = planDomainService.abrirTramoEstado(planExistente, EstadoPlan.NO_PUBLICADO, null);

        return planMapper.toCambioEstadoResponse(planDespublicado);

    }

    /**
     * Deshabilita un plan (transición terminal e irreversible). Restrictiva: rechaza si
     * hay turnos vivos cubiertos por el plan. No rige la regla del "último plan no
     * deshabilitado de una obra social activa" (eliminada en v3).
     *
     * @param id {@code UUID} identificador de la ruta
     * @param deshabilitarPlanRequest {@code DeshabilitarPlanRequest} motivo opcional
     * @return {@code CambioEstadoPlanResponse} el plan deshabilitado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya está deshabilitado o tiene uso vigente
     */
    @Transactional
    public CambioEstadoPlanResponse deshabilitarPlan(UUID id, DeshabilitarPlanRequest deshabilitarPlanRequest) {

        log.info("Deshabilitación de plan iniciada: id={}", id);

        Plan planExistente = planDomainService.findPlanById(id);

        planDomainService.validatePuedeDeshabilitar(planExistente);
        planDomainService.validateSinUsoVigente(planExistente);

        //TODO cascada de escritura: bajar ObraSocialPlanPrestacion y ObraSocialPaciente asociados (fuera de alcance).

        Plan planDeshabilitado = planDomainService.abrirTramoEstado(
                planExistente, EstadoPlan.DESHABILITADO, deshabilitarPlanRequest.motivo());

        return planMapper.toCambioEstadoResponse(planDeshabilitado);

    }

    /**
     * Busca un plan por su identificador.
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code GetPlanResponse} el plan encontrado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan no existe
     */
    @Transactional(readOnly = true)
    public GetPlanResponse findPlanById(UUID id) {

        log.info("Búsqueda de plan iniciada: id={}", id);

        Plan planExistente = planDomainService.findPlanById(id);

        return planMapper.toGetResponse(planExistente);

    }

    /**
     * Lista los planes de una obra social determinada.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code List<ListPlanResponse>} lista de planes de esa obra social
     */
    @Transactional(readOnly = true)
    public List<ListPlanResponse> findPlanesByObraSocial(UUID obraSocialId) {

        log.info("Listado de planes iniciado: obraSocialId={}", obraSocialId);

        return planMapper.toListResponses(planQueryService.findPlanesByObraSocial(obraSocialId));

    }

    //endregion

}
