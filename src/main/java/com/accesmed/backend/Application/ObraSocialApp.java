package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.ObraSocial.Request.CreateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Request.CreatePlanAnidadoRequest;
import com.accesmed.backend.Records.ObraSocial.Request.UpdateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Response.CreateObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetPlanAnidadoResponse;
import com.accesmed.backend.Records.ObraSocial.Response.ListObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.SoftDeleteObraSocialResponse;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ObraSocialMapper;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import com.accesmed.backend.Services.QueryServices.ObraSocialQueryService;
import com.accesmed.backend.Services.QueryServices.PlanQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        obraSocialDomainService.validateCodigoObraSocialIsUnique(createObraSocialRequest.codigo());
        obraSocialDomainService.validateNombreObraSocialIsUnique(createObraSocialRequest.nombre());

        ObraSocial obraSocialNueva = obraSocialMapper.toEntity(createObraSocialRequest);
        ObraSocial obraSocialGuardada = obraSocialDomainService.saveObraSocial(obraSocialNueva);

        List<GetPlanAnidadoResponse> planesResponse = new ArrayList<>();
        for (CreatePlanAnidadoRequest planAnidado : createObraSocialRequest.planes()) {
            planDomainService.validateCodigoPlanIsUnique(obraSocialGuardada.getId(), planAnidado.codigo());
            planDomainService.validateNombrePlanIsUnique(obraSocialGuardada.getId(), planAnidado.nombre());

            Plan planNuevo = planMapper.toEntity(planAnidado);
            planNuevo.setObraSocial(obraSocialGuardada);

            Plan planGuardado = planDomainService.savePlan(planNuevo);
            planDomainService.abrirTramoInicial(planGuardado);

            planesResponse.add(planMapper.toGetPlanAnidadoResponse(planGuardado));
        }

        return obraSocialMapper.toCreateResponse(obraSocialGuardada, planesResponse);

    }

    /**
     * Actualiza una obra social existente.
     *
     * @param id {@code UUID} identificador de la ruta
     * @param updateObraSocialRequest {@code UpdateObraSocialRequest} datos a actualizar
     * @return {@code GetObraSocialResponse} la obra social actualizada, con sus planes
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre son duplicados
     */
    @Transactional
    public GetObraSocialResponse updateObraSocial(UUID id, UpdateObraSocialRequest updateObraSocialRequest) {

        log.info("Actualización de obra social iniciada: id={}", id);

        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(id);

        if (updateObraSocialRequest.codigo() != null) {
            obraSocialDomainService.validateCodigoObraSocialIsUnique(updateObraSocialRequest.codigo(), id);
        }
        if (updateObraSocialRequest.nombre() != null) {
            obraSocialDomainService.validateNombreObraSocialIsUnique(updateObraSocialRequest.nombre(), id);
        }

        obraSocialMapper.updateObraSocial(obraSocialExistente, updateObraSocialRequest);

        ObraSocial obraSocialActualizada = obraSocialDomainService.saveObraSocial(obraSocialExistente);

        List<GetPlanAnidadoResponse> planesResponse = planMapper
                .toGetPlanAnidadoResponses(planQueryService.findPlanesByObraSocial(id));

        return obraSocialMapper.toGetResponse(obraSocialActualizada, planesResponse);

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

        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(id);

        List<Plan> planesNoDeshabilitados = planQueryService.findPlanesNoDeshabilitadosByObraSocial(id);

        for (Plan plan : planesNoDeshabilitados) {
            planDomainService.validateSinUsoVigente(plan);
        }

        for (Plan plan : planesNoDeshabilitados) {
            planDomainService.abrirTramoEstado(plan, EstadoPlan.DESHABILITADO, "Baja de obra social");
        }

        obraSocialDomainService.softDeleteObraSocial(obraSocialExistente, "Baja de obra social");

        return obraSocialMapper.toSoftDeleteResponse(obraSocialExistente);

    }

    /**
     * Busca una obra social activa por su identificador, con sus planes.
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code GetObraSocialResponse} la obra social encontrada, con sus planes
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la obra social no existe
     */
    @Transactional(readOnly = true)
    public GetObraSocialResponse findObraSocialById(UUID id) {

        log.info("Búsqueda de obra social iniciada: id={}", id);

        ObraSocial obraSocialExistente = obraSocialDomainService.findObraSocialById(id);

        List<GetPlanAnidadoResponse> planesResponse = planMapper
                .toGetPlanAnidadoResponses(planQueryService.findPlanesByObraSocial(id));

        return obraSocialMapper.toGetResponse(obraSocialExistente, planesResponse);

    }

    /**
     * Lista todas las obras sociales activas.
     *
     * @return {@code List<ListObraSocialResponse>} lista de obras sociales activas
     */
    @Transactional(readOnly = true)
    public List<ListObraSocialResponse> findObrasSociales() {

        log.info("Listado de obras sociales iniciado");

        return obraSocialMapper.toListResponses(obraSocialQueryService.findAllObrasSociales());

    }

    //endregion

}
