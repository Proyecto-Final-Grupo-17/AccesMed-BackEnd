package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.ObraSocial.Criteria.ObraSocialCriteria;
import com.accesmed.backend.Records.ObraSocial.Request.CreateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Request.CreatePlanAnidadoRequest;
import com.accesmed.backend.Records.ObraSocial.Request.UpdateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Response.CreateObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetPlanAnidadoResponse;
import com.accesmed.backend.Records.ObraSocial.Response.ListObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.SoftDeleteObraSocialResponse;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPlanDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ObraSocialMapper;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.ObraSocialQueryService;
import com.accesmed.backend.Services.QueryServices.PlanQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso de Obra Social. Orquesta el flujo completo de los endpoints (creación
 * atómica con planes, actualización, baja restrictiva por transitividad) validando
 * reglas de negocio y coordinando los services de {@code ObraSocial} y {@code Plan}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialApp {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanDomainService planDomainService;
    private final HistoricoEstadoPlanDomainService historicoEstadoPlanDomainService;
    private final TurnoDomainService turnoDomainService;
    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialMapper obraSocialMapper;
    private final PlanMapper planMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una obra social nueva, opcionalmente junto con sus planes iniciales, en una
     * única transacción atómica. Cada plan nace en estado {@code NO_PUBLICADO}.
     *
     * @param createObraSocialRequest {@code CreateObraSocialRequest} datos de la obra social y,
     *        opcionalmente, sus planes
     * @return {@code CreateObraSocialResponse} la obra social creada, con sus planes (si se enviaron)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre de la
     *         obra social (o de algún plan) ya existen
     */
    @Transactional
    public CreateObraSocialResponse createObraSocial(CreateObraSocialRequest createObraSocialRequest) {

        log.info("Creación de obra social iniciada: código={}", createObraSocialRequest.codigo());

        //Validar que el código y el nombre sean únicos
        obraSocialDomainService.validateCodigoObraSocialIsUnique(createObraSocialRequest.codigo());
        obraSocialDomainService.validateNombreObraSocialIsUnique(createObraSocialRequest.nombre());

        //Mapear y guardar la obra social
        ObraSocial obraSocialNueva = obraSocialMapper.toEntity(createObraSocialRequest);
        ObraSocial obraSocialGuardada = obraSocialDomainService.saveObraSocial(obraSocialNueva);

        //Los planes iniciales son opcionales
        List<CreatePlanAnidadoRequest> planesRequest = createObraSocialRequest.planes();
        List<GetPlanAnidadoResponse> planesResponse = new ArrayList<>();
        if (planesRequest != null && !planesRequest.isEmpty()) {

            //Validar que el código y el nombre de cada plan sean únicos en la obra social
            for (CreatePlanAnidadoRequest planAnidado : planesRequest) {
                planDomainService.validateCodigoPlanIsUnique(obraSocialGuardada.getId(), planAnidado.codigo());
                planDomainService.validateNombrePlanIsUnique(obraSocialGuardada.getId(), planAnidado.nombre());
            }

            //Mapear y guardar los planes iniciales asociados
            List<Plan> planesNuevos = planMapper.toEntities(planesRequest, obraSocialGuardada);
            List<Plan> planesGuardados = planDomainService.savePlanes(planesNuevos);

            //Abrir el tramo inicial de histórico de cada plan (nace en NO_PUBLICADO; requiere el id ya asignado)
            planesGuardados.forEach(historicoEstadoPlanDomainService::openHistoricoInicialPlan);

            //Recién abierto el tramo inicial, el estado vigente de todos es NO_PUBLICADO
            planesResponse = planesGuardados.stream()
                    .map(plan -> planMapper.toGetPlanAnidadoResponse(plan, EstadoPlan.NO_PUBLICADO))
                    .toList();
        }

        //Devolver response mapeado
        CreateObraSocialResponse createObraSocialResponse = obraSocialMapper.toCreateResponse(obraSocialGuardada, planesResponse);
        return createObraSocialResponse;

    }

    /**
     * Actualiza una obra social existente.
     *
     * @param updateObraSocialRequest {@code UpdateObraSocialRequest} datos a actualizar, incluyendo
     *        el id de la obra social (ya validado contra la ruta en el Controller)
     * @return {@code GetObraSocialResponse} la obra social actualizada, con sus planes
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre son duplicados
     */
    @Transactional
    public GetObraSocialResponse updateObraSocial(UpdateObraSocialRequest updateObraSocialRequest) {

        UUID id = updateObraSocialRequest.id();

        log.info("Actualización de obra social iniciada: id={}", id);

        //Buscar la obra social activa
        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(id);

        //Validar unicidad de los campos que vinieron
        if (updateObraSocialRequest.codigo() != null) {
            obraSocialDomainService.validateCodigoObraSocialIsUnique(updateObraSocialRequest.codigo(), id);
        }
        if (updateObraSocialRequest.nombre() != null) {
            obraSocialDomainService.validateNombreObraSocialIsUnique(updateObraSocialRequest.nombre(), id);
        }

        //Aplicar los cambios y guardar
        obraSocialMapper.updateObraSocial(obraSocialExistente, updateObraSocialRequest);
        ObraSocial obraSocialActualizada = obraSocialDomainService.saveObraSocial(obraSocialExistente);

        //Devolver response mapeado, con los planes de la obra social (estado vigente por lote)
        List<GetPlanAnidadoResponse> planesResponse = mapPlanesAnidados(planDomainService.findPlanesByObraSocial(id));
        GetObraSocialResponse getObraSocialResponse = obraSocialMapper.toGetResponse(obraSocialActualizada, planesResponse);
        return getObraSocialResponse;

    }

    /**
     * Da de baja una obra social (baja lógica restrictiva por transitividad): evalúa la
     * precondición de deshabilitación de cada plan no deshabilitado; si alguno no puede,
     * rechaza toda la operación. Si todos pueden, deshabilita en cascada esos planes y da
     * de baja la obra social.
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code SoftDeleteObraSocialResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si algún plan no puede deshabilitarse
     */
    @Transactional
    public SoftDeleteObraSocialResponse softDeleteObraSocial(UUID id) {

        log.info("Baja de obra social iniciada: id={}", id);

        //Buscar la obra social activa
        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(id);

        //Validar que cada plan no deshabilitado pueda deshabilitarse (sin turnos vivos); si alguno falla, rechazar toda la operación
        List<Plan> planesNoDeshabilitados = planDomainService.findPlanesNoDeshabilitadosByObraSocial(id);
        for (Plan plan : planesNoDeshabilitados) {
            turnoDomainService.validateSinTurnosVivosDePlan(plan.getId());
        }

        //Deshabilitar en cascada los planes no deshabilitados, arrastrando sus coberturas (A5)
        for (Plan plan : planesNoDeshabilitados) {
            obraSocialPacienteDomainService.softDeleteByPlan(plan.getId(), "Baja de obra social");
            historicoEstadoPlanDomainService.changeEstadoPlan(plan.getId(), EstadoPlan.DESHABILITADO, "Baja de obra social");
        }

        //TODO (A5, paso 1): dar de baja las ObraSocialPlanPrestacion de cada plan deshabilitado.
        // Sin módulo (repo/service/App/controller) al que delegarlo todavía. Ver
        // Docs/Planes/auditoria-v3-y-feature-agenda.md.

        //Dar de baja la obra social
        obraSocialDomainService.softDeleteObraSocial(obraSocialExistente, "Baja de obra social");

        //Devolver response mapeado
        SoftDeleteObraSocialResponse softDeleteObraSocialResponse = obraSocialMapper.toSoftDeleteResponse(obraSocialExistente);
        return softDeleteObraSocialResponse;

    }

    /**
     * Mapea una lista de planes a sus responses anidados, resolviendo el estado vigente de
     * todos en una única consulta (evita N+1) y alimentándolo a cada response.
     *
     * @param planes {@code List<Plan>} planes a mapear
     * @return {@code List<GetPlanAnidadoResponse>} responses anidados con su estado vigente
     */
    private List<GetPlanAnidadoResponse> mapPlanesAnidados(List<Plan> planes) {

        Map<UUID, EstadoPlan> estadosVigentes = historicoEstadoPlanDomainService.getEstadosVigentes(
                planes.stream().map(Plan::getId).toList());

        return planes.stream()
                .map(plan -> planMapper.toGetPlanAnidadoResponse(plan, estadosVigentes.get(plan.getId())))
                .toList();

    }

    //endregion

}
