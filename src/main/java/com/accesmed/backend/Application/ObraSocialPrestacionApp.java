package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.ObraSocialPrestacion.Request.AssignObraSocialPrestacionRequest;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.GetObraSocialPrestacionResponse;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.UnassignObraSocialPrestacionResponse;
import com.accesmed.backend.Services.DomainServices.ObraSocialPlanPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ObraSocialPlanPrestacionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso de Obra Social-Prestación. Orquesta el flujo de asignar y desasignar una
 * prestación a un plan de obra social, validando reglas de negocio y coordinando los
 * services de {@code ObraSocialPlanPrestacion}, {@code Plan}, {@code Prestacion} y
 * {@code Turno}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialPrestacionApp {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPlanPrestacionDomainService obraSocialPlanPrestacionDomainService;
    private final PlanDomainService planDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final TurnoDomainService turnoDomainService;
    private final ObraSocialPlanPrestacionMapper obraSocialPlanPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna una prestación existente a un plan existente, con sus condiciones de
     * cobertura.
     *
     * @param assignObraSocialPrestacionRequest {@code AssignObraSocialPrestacionRequest} datos de la cobertura
     * @return {@code GetObraSocialPrestacionResponse} la cobertura creada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan o la
     *         prestación no existen (activos)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una cobertura
     *         activa entre ambos
     */
    @Transactional
    public GetObraSocialPrestacionResponse assignPrestacion(AssignObraSocialPrestacionRequest assignObraSocialPrestacionRequest) {

        log.info("Asignación de prestación a plan iniciada: plan={}, prestación={}",
                assignObraSocialPrestacionRequest.planId(), assignObraSocialPrestacionRequest.prestacionId());

        //Buscar plan y prestación activos
        Plan planExistente = planDomainService.findPlanActivoById(assignObraSocialPrestacionRequest.planId());
        Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaById(assignObraSocialPrestacionRequest.prestacionId());

        //Validar que no exista ya una cobertura activa entre ambos, y la coherencia de la modalidad
        obraSocialPlanPrestacionDomainService.validateSinCoberturaActiva(planExistente.getId(), prestacionExistente.getId());
        obraSocialPlanPrestacionDomainService.validateCoherenciaCobertura(assignObraSocialPrestacionRequest.modalidadCobertura(),
                assignObraSocialPrestacionRequest.porcentajeCobertura(), assignObraSocialPrestacionRequest.coseguro());

        //Mapear, completar relaciones y guardar
        ObraSocialPlanPrestacion coberturaNueva = obraSocialPlanPrestacionMapper.toEntity(assignObraSocialPrestacionRequest);
        coberturaNueva.setPlan(planExistente);
        coberturaNueva.setPrestacion(prestacionExistente);
        ObraSocialPlanPrestacion coberturaGuardada = obraSocialPlanPrestacionDomainService.saveObraSocialPlanPrestacion(coberturaNueva);

        //Devolver response mapeado
        GetObraSocialPrestacionResponse getObraSocialPrestacionResponse = obraSocialPlanPrestacionMapper.toGetResponse(coberturaGuardada);
        return getObraSocialPrestacionResponse;

    }

    /**
     * Desasigna una prestación de un plan (baja lógica de la cobertura). Restrictiva:
     * rechaza si hay turnos vivos cubiertos por el par plan-prestación.
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code UnassignObraSocialPrestacionResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la cobertura no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos cubiertos
     *         por el par plan-prestación
     */
    @Transactional
    public UnassignObraSocialPrestacionResponse unassignPrestacion(UUID id) {

        log.info("Desasignación de prestación de plan iniciada: id={}", id);

        //Buscar la cobertura activa
        ObraSocialPlanPrestacion coberturaExistente = obraSocialPlanPrestacionDomainService.findObraSocialPlanPrestacionActivaById(id);

        //Piso duro: no se puede desasignar si hay turnos vivos del par plan-prestación
        UUID planId = coberturaExistente.getPlan().getId();
        UUID prestacionId = coberturaExistente.getPrestacion().getId();
        Optional<ZonedDateTime> fechaMaximaTurnoVivo = turnoDomainService.findMaxFechaHoraInicioTurnoVivoDePlanYPrestacion(planId, prestacionId);
        if (fechaMaximaTurnoVivo.isPresent()) {
            log.warn("No se pudo desasignar la cobertura {}: hay turnos vivos del par plan={}, prestación={}, el más lejano el {}",
                    id, planId, prestacionId, fechaMaximaTurnoVivo.get());
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_PLAN_PRESTACION_CON_TURNOS_VIVOS",
                    "El par plan-prestación tiene turnos vivos, el más lejano el " + fechaMaximaTurnoVivo.get()
                            + ". No se puede desasignar.");
        }

        //Dar de baja
        obraSocialPlanPrestacionDomainService.softDeleteObraSocialPlanPrestacion(coberturaExistente, "Desasignación de prestación de plan");

        //Devolver response mapeado
        UnassignObraSocialPrestacionResponse unassignObraSocialPrestacionResponse =
                obraSocialPlanPrestacionMapper.toUnassignResponse(coberturaExistente);
        return unassignObraSocialPrestacionResponse;

    }

    //endregion

}
