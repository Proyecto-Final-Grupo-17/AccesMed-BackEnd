package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.Paciente.Criteria.PacienteCriteria;
import com.accesmed.backend.Records.Paciente.Request.AsignarObraSocialAnidadaRequest;
import com.accesmed.backend.Records.Paciente.Request.CreatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Request.UpdatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Response.CreatePacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.GetObraSocialAnidadaResponse;
import com.accesmed.backend.Records.Paciente.Response.GetPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.ListPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.SoftDeletePacienteResponse;
import com.accesmed.backend.Services.DomainServices.ObraSocialDomainService;
import com.accesmed.backend.Services.DomainServices.ObraSocialPacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PacienteDomainService;
import com.accesmed.backend.Services.DomainServices.PlanDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ObraSocialPacienteMapper;
import com.accesmed.backend.Services.Mappers.PacienteMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.PacienteQueryService;
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
 * Caso de uso de Paciente. Orquesta el flujo completo de los endpoints (creación atómica
 * con sus coberturas de obra social, actualización, baja restrictiva) validando reglas de
 * negocio y coordinando los services de {@code Paciente}, {@code ObraSocialPaciente},
 * {@code ObraSocial}, {@code Plan} y {@code Turno}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacienteApp {

    //region ========== Dependencias ==========

    //Domain Services
    private final PacienteDomainService pacienteDomainService;
    private final ObraSocialPacienteDomainService obraSocialPacienteDomainService;
    private final ObraSocialDomainService obraSocialDomainService;
    private final PlanDomainService planDomainService;
    private final TurnoDomainService turnoDomainService;

    //Mappers
    private final PacienteMapper pacienteMapper;
    private final ObraSocialPacienteMapper obraSocialPacienteMapper;

    //Query Services
    private final PacienteQueryService pacienteQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un paciente nuevo junto con las coberturas de obra social existentes que
     * declara, en una única transacción atómica.
     *
     * @param createPacienteRequest {@code CreatePacienteRequest} datos del paciente y sus coberturas
     * @return {@code CreatePacienteResponse} el paciente creado, con sus coberturas asignadas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si alguna obra
     *         social o plan no existen (activos)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el DNI, número de teléfono o
     *         email ya existen, o si algún plan no pertenece a la obra social indicada
     */
    @Transactional
    public CreatePacienteResponse createPaciente(CreatePacienteRequest createPacienteRequest) {

        log.info("Creación de paciente iniciada: dni={}", createPacienteRequest.dni());

        //Validar que el DNI, el número de teléfono y el email sean únicos
        pacienteDomainService.validateDniPacienteIsUnique(createPacienteRequest.dni());
        pacienteDomainService.validateNumeroTelefonoPacienteIsUnique(createPacienteRequest.numeroTelefono());
        pacienteDomainService.validateEmailPacienteIsUnique(createPacienteRequest.email());

        //Mapear y guardar el paciente
        Paciente pacienteNuevo = pacienteMapper.toEntity(createPacienteRequest);
        Paciente pacienteGuardado = pacienteDomainService.savePaciente(pacienteNuevo);

        //Asignar cada cobertura de obra social anidada
        List<GetObraSocialAnidadaResponse> obrasSocialesResponse = new ArrayList<>();
        List<AsignarObraSocialAnidadaRequest> obrasSocialesRequest = createPacienteRequest.obrasSociales();
        if (obrasSocialesRequest != null) {
            for (AsignarObraSocialAnidadaRequest obraSocialAnidada : obrasSocialesRequest) {
                obraSocialDomainService.findObraSocialById(obraSocialAnidada.obraSocialId());
                Plan planExistente = planDomainService.findPlanActivoById(obraSocialAnidada.planId());
                obraSocialPacienteDomainService.validatePlanPerteneceAObraSocial(planExistente, obraSocialAnidada.obraSocialId());

                ObraSocialPaciente obraSocialPacienteNueva = new ObraSocialPaciente(
                        obraSocialAnidada.nroSocio(), pacienteGuardado, planExistente);

                ObraSocialPaciente obraSocialPacienteGuardada = obraSocialPacienteDomainService.saveObraSocialPaciente(obraSocialPacienteNueva);
                obrasSocialesResponse.add(obraSocialPacienteMapper.toGetObraSocialAnidadaResponse(obraSocialPacienteGuardada));
            }
        }

        //Devolver response mapeado
        CreatePacienteResponse createPacienteResponse = pacienteMapper.toCreateResponse(pacienteGuardado, obrasSocialesResponse);
        return createPacienteResponse;

    }

    /**
     * Actualiza un paciente existente.
     *
     * @param updatePacienteRequest {@code UpdatePacienteRequest} datos a actualizar, incluyendo
     *        el id del paciente (ya validado contra la ruta en el Controller)
     * @return {@code GetPacienteResponse} el paciente actualizado, con sus coberturas asignadas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el DNI, número de teléfono o
     *         email son duplicados
     */
    @Transactional
    public GetPacienteResponse updatePaciente(UpdatePacienteRequest updatePacienteRequest) {

        UUID id = updatePacienteRequest.id();

        log.info("Actualización de paciente iniciada: id={}", id);

        //Buscar el paciente activo
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoById(id);

        //Validar unicidad de los campos que vinieron
        if (updatePacienteRequest.dni() != null) {
            pacienteDomainService.validateDniPacienteIsUnique(updatePacienteRequest.dni(), id);
        }
        if (updatePacienteRequest.numeroTelefono() != null) {
            pacienteDomainService.validateNumeroTelefonoPacienteIsUnique(updatePacienteRequest.numeroTelefono(), id);
        }
        if (updatePacienteRequest.email() != null) {
            pacienteDomainService.validateEmailPacienteIsUnique(updatePacienteRequest.email(), id);
        }

        //Aplicar los cambios y guardar
        pacienteMapper.updatePaciente(pacienteExistente, updatePacienteRequest);
        Paciente pacienteActualizado = pacienteDomainService.savePaciente(pacienteExistente);

        //Devolver response mapeado, con las coberturas del paciente
        List<GetObraSocialAnidadaResponse> obrasSocialesResponse = obraSocialPacienteMapper
                .toGetObraSocialAnidadaResponses(obraSocialPacienteDomainService.findCoberturasActivasByPaciente(id));
        GetPacienteResponse getPacienteResponse = pacienteMapper.toGetResponse(pacienteActualizado, obrasSocialesResponse);
        return getPacienteResponse;

    }

    /**
     * Da de baja un paciente (baja lógica restrictiva contra turnos vivos).
     *
     * @param id {@code UUID} identificador del paciente
     * @return {@code SoftDeletePacienteResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el paciente no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el paciente tiene turnos vivos
     */
    @Transactional
    public SoftDeletePacienteResponse softDeletePaciente(UUID id) {

        log.info("Baja de paciente iniciada: id={}", id);

        //Buscar el paciente activo
        Paciente pacienteExistente = pacienteDomainService.findPacienteActivoById(id);

        //Validar que no tenga turnos vivos
        turnoDomainService.validateSinTurnosVivosDePaciente(id);

        //Dar de baja
        pacienteDomainService.softDeletePaciente(pacienteExistente, "Baja de paciente");

        //Devolver response mapeado
        SoftDeletePacienteResponse softDeletePacienteResponse = pacienteMapper.toSoftDeleteResponse(pacienteExistente);
        return softDeletePacienteResponse;

    }

    /**
     * Busca el paciente activo que cumple el criteria de filtrado dinámico proporcionado,
     * con sus coberturas de obra social. A diferencia de {@link #findPacientes}, devuelve
     * un único paciente (no paginado) — pensado para criterios que identifican un paciente
     * puntual (ej. {@code id.equals}).
     *
     * @param pacienteCriteria {@code PacienteCriteria} filtros a aplicar
     * @return {@code GetPacienteResponse} el paciente encontrado, con sus coberturas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún paciente cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetPacienteResponse findPacienteByCriteria(PacienteCriteria pacienteCriteria) {

        log.info("Búsqueda de paciente iniciada: criteria={}", pacienteCriteria);

        //Buscar el paciente y sus coberturas
        Paciente pacienteExistente = pacienteQueryService.findPacienteByCriteria(pacienteCriteria);
        List<GetObraSocialAnidadaResponse> obrasSocialesResponse = obraSocialPacienteMapper
                .toGetObraSocialAnidadaResponses(obraSocialPacienteDomainService.findCoberturasActivasByPaciente(pacienteExistente.getId()));

        //Devolver response mapeado
        GetPacienteResponse getPacienteResponse = pacienteMapper.toGetResponse(pacienteExistente, obrasSocialesResponse);
        return getPacienteResponse;

    }

    /**
     * Lista pacientes activos según el criteria de filtrado dinámico proporcionado.
     *
     * @param pacienteCriteria {@code PacienteCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListPacienteResponse>} página de pacientes que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListPacienteResponse> findPacientes(PacienteCriteria pacienteCriteria, Pageable pageable) {

        log.info("Listado de pacientes iniciado: criteria={}, page={}", pacienteCriteria, pageable);

        //Buscar pacientes que cumplen el criteria, paginados
        Page<Paciente> pacientesPagina = pacienteQueryService.findByCriteria(pacienteCriteria, pageable);

        //Devolver response mapeado
        PageResponse<ListPacienteResponse> pageResponse = PageResponse.from(pacientesPagina, pacienteMapper::toListResponse);
        return pageResponse;

    }

    //endregion

}
