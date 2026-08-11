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
    private final ObraSocialQueryService obraSocialQueryService;
    private final PlanQueryService planQueryService;
    private final ObraSocialMapper obraSocialMapper;
    private final PlanMapper planMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una obra social nueva junto con sus planes iniciales, en una única
     * transacción atómica. Cada plan nace en estado {@code NO_PUBLICADO}.
     *
     * @param createObraSocialRequest {@code CreateObraSocialRequest} datos de la obra social y sus planes
     * @return {@code CreateObraSocialResponse} la obra social creada, con sus planes
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

        //Crear cada plan inicial asociado
        List<GetPlanAnidadoResponse> planesResponse = new ArrayList<>();
        for (CreatePlanAnidadoRequest planAnidado : createObraSocialRequest.planes()) {
            planDomainService.validateCodigoPlanIsUnique(obraSocialGuardada.getId(), planAnidado.codigo());
            planDomainService.validateNombrePlanIsUnique(obraSocialGuardada.getId(), planAnidado.nombre());

            Plan planNuevo = planMapper.toEntity(planAnidado);
            planNuevo.setObraSocial(obraSocialGuardada);
            historicoEstadoPlanDomainService.seedEstadoInicialPlan(planNuevo);

            Plan planGuardado = planDomainService.savePlan(planNuevo);
            historicoEstadoPlanDomainService.openHistoricoInicialPlan(planGuardado);

            planesResponse.add(planMapper.toGetPlanAnidadoResponse(planGuardado));
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

        //Devolver response mapeado, con los planes de la obra social
        List<GetPlanAnidadoResponse> planesResponse = planMapper
                .toGetPlanAnidadoResponses(planQueryService.findPlanesByObraSocial(id));
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
        List<Plan> planesNoDeshabilitados = planQueryService.findPlanesNoDeshabilitadosByObraSocial(id);
        for (Plan plan : planesNoDeshabilitados) {
            turnoDomainService.validateSinTurnosVivosDePlan(plan.getId());
        }

        //Deshabilitar en cascada los planes no deshabilitados
        for (Plan plan : planesNoDeshabilitados) {
            historicoEstadoPlanDomainService.changeEstadoPlan(plan.getId(), EstadoPlan.DESHABILITADO, "Baja de obra social");
        }

        //Dar de baja la obra social
        obraSocialDomainService.softDeleteObraSocial(obraSocialExistente, "Baja de obra social");

        //Devolver response mapeado
        SoftDeleteObraSocialResponse softDeleteObraSocialResponse = obraSocialMapper.toSoftDeleteResponse(obraSocialExistente);
        return softDeleteObraSocialResponse;

    }

    /**
     * Busca la obra social activa que cumple el criteria de filtrado dinámico
     * proporcionado, con sus planes. A diferencia de {@link #findObrasSociales}, devuelve
     * una única obra social (no paginada) — pensado para criterios que identifican una obra
     * social puntual (ej. {@code id.equals}).
     *
     * @param obraSocialCriteria {@code ObraSocialCriteria} filtros a aplicar
     * @return {@code GetObraSocialResponse} la obra social encontrada, con sus planes
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna obra social cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetObraSocialResponse findObraSocialByCriteria(ObraSocialCriteria obraSocialCriteria) {

        log.info("Búsqueda de obra social iniciada: criteria={}", obraSocialCriteria);

        //Buscar la obra social y sus planes
        ObraSocial obraSocialExistente = obraSocialQueryService.findObraSocialByCriteria(obraSocialCriteria);
        List<GetPlanAnidadoResponse> planesResponse = planMapper
                .toGetPlanAnidadoResponses(planQueryService.findPlanesByObraSocial(obraSocialExistente.getId()));

        //Devolver response mapeado
        GetObraSocialResponse getObraSocialResponse = obraSocialMapper.toGetResponse(obraSocialExistente, planesResponse);
        return getObraSocialResponse;

    }

    /**
     * Lista obras sociales activas según el criteria de filtrado dinámico proporcionado.
     *
     * @param obraSocialCriteria {@code ObraSocialCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListObraSocialResponse>} página de obras sociales que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListObraSocialResponse> findObrasSociales(ObraSocialCriteria obraSocialCriteria, Pageable pageable) {

        log.info("Listado de obras sociales iniciado: criteria={}, page={}", obraSocialCriteria, pageable);

        //Buscar obras sociales que cumplen el criteria, paginadas
        Page<ObraSocial> obrasSocialesPagina = obraSocialQueryService.findByCriteria(obraSocialCriteria, pageable);

        //Devolver response mapeado
        PageResponse<ListObraSocialResponse> pageResponse = PageResponse.from(obrasSocialesPagina, obraSocialMapper::toListResponse);
        return pageResponse;

    }

    //endregion

}
