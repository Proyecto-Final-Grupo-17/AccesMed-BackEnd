package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.ObraSocialPaciente.Request.AssignObraSocialPacienteRequest;
import com.accesmed.backend.Records.ObraSocialPaciente.Response.GetObraSocialPacienteResponse;
import com.accesmed.backend.Records.ObraSocialPaciente.Response.SoftDeleteObraSocialPacienteResponse;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ObraSocialPacienteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso de Obra Social-Paciente. Orquesta el flujo de asignar y desasignar una
 * cobertura de obra social a un paciente, validando reglas de negocio y coordinando los
 * services de {@code ObraSocialPaciente}, {@code Paciente}, {@code ObraSocial} y {@code Plan}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialPacienteApp {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final PacienteDomainService pacienteDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanDomainService planDomainService;
    private final ObraSocialPacienteMapper obraSocialPacienteMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna a un paciente existente una cobertura sobre un plan existente de una obra
     * social existente.
     *
     * @param assignObraSocialPacienteRequest {@code AssignObraSocialPacienteRequest} datos de la cobertura
     * @return {@code GetObraSocialPacienteResponse} la cobertura creada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente, la
     *         obra social o el plan no existen (activos)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plan no pertenece a la obra
     *         social indicada, o ya existe una cobertura activa entre ambos
     */
    @Transactional
    public GetObraSocialPacienteResponse assignObraSocial(AssignObraSocialPacienteRequest assignObraSocialPacienteRequest) {

        log.info("Asignación de obra social a paciente iniciada: paciente={}, plan={}",
                assignObraSocialPacienteRequest.pacienteId(), assignObraSocialPacienteRequest.planId());

        //Buscar paciente, obra social y plan activos
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoById(assignObraSocialPacienteRequest.pacienteId());
        obraSocialDomainService.findObraSocialById(assignObraSocialPacienteRequest.obraSocialId());
        Plan planExistente = planDomainService.findPlanActivoById(assignObraSocialPacienteRequest.planId());

        //Validar que el plan pertenezca a la obra social indicada y que no haya cobertura activa duplicada
        obraSocialPacienteDomainService.validatePlanPerteneceAObraSocial(planExistente, assignObraSocialPacienteRequest.obraSocialId());
        obraSocialPacienteDomainService.validateSinCoberturaActiva(pacienteExistente.getId(), planExistente.getId());

        //Construir y guardar
        ObraSocialPaciente obraSocialPacienteNueva = new ObraSocialPaciente(
                assignObraSocialPacienteRequest.nroSocio(), pacienteExistente, planExistente);
        ObraSocialPaciente obraSocialPacienteGuardada = obraSocialPacienteDomainService.saveObraSocialPaciente(obraSocialPacienteNueva);

        //Devolver response mapeado
        GetObraSocialPacienteResponse getObraSocialPacienteResponse = obraSocialPacienteMapper.toGetResponse(obraSocialPacienteGuardada);
        return getObraSocialPacienteResponse;

    }

    /**
     * Desasigna una cobertura de obra social de un paciente (baja lógica de la cobertura).
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code SoftDeleteObraSocialPacienteResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la cobertura no existe
     */
    @Transactional
    public SoftDeleteObraSocialPacienteResponse unassignObraSocial(UUID id) {

        log.info("Desasignación de obra social de paciente iniciada: id={}", id);

        //Buscar la cobertura activa
        ObraSocialPaciente obraSocialPacienteExistente = obraSocialPacienteDomainService.findObraSocialPacienteActivaById(id);

        //Dar de baja
        obraSocialPacienteDomainService.softDeleteObraSocialPaciente(obraSocialPacienteExistente, "Desasignación de cobertura de obra social");

        //Devolver response mapeado
        SoftDeleteObraSocialPacienteResponse softDeleteObraSocialPacienteResponse =
                obraSocialPacienteMapper.toSoftDeleteResponse(obraSocialPacienteExistente);
        return softDeleteObraSocialPacienteResponse;

    }

    //endregion

}
