package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.MedicoPrestacion.Request.AssignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Response.GetMedicoPrestacionResponse;
import com.accesmed.backend.Records.MedicoPrestacion.Response.SoftDeleteMedicoPrestacionResponse;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.MedicoPrestacionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso de Médico-Prestación. Orquesta el flujo de asignar y desasignar una
 * prestación a un médico, validando reglas de negocio y coordinando los services de
 * {@code MedicoPrestacion}, {@code Medico} y {@code Prestacion}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoPrestacionApp {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final MedicoPrestacionMapper medicoPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna una prestación existente a un médico existente.
     *
     * @param assignMedicoPrestacionRequest {@code AssignMedicoPrestacionRequest} datos de la asignación
     * @return {@code GetMedicoPrestacionResponse} la asignación creada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el médico o
     *         la prestación no existen (activos)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si las especialidades no coinciden
     *         o ya existe una asignación activa entre ambos
     */
    @Transactional
    public GetMedicoPrestacionResponse assignPrestacion(AssignMedicoPrestacionRequest assignMedicoPrestacionRequest) {

        log.info("Asignación de prestación a médico iniciada: médico={}, prestación={}",
                assignMedicoPrestacionRequest.medicoId(), assignMedicoPrestacionRequest.prestacionId());

        //Buscar médico y prestación activos
        Medico medicoExistente = medicoDomainService.findMedicoActivoById(assignMedicoPrestacionRequest.medicoId());
        Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaById(assignMedicoPrestacionRequest.prestacionId());

        //Validar especialidad coincidente y que no haya asignación activa duplicada
        medicoPrestacionDomainService.validateEspecialidadCoincide(medicoExistente, prestacionExistente);
        medicoPrestacionDomainService.validateSinAsignacionActiva(medicoExistente.getId(), prestacionExistente.getId());

        //Mapear, completar relaciones y guardar
        MedicoPrestacion medicoPrestacionNueva = medicoPrestacionMapper.toEntity(assignMedicoPrestacionRequest);
        medicoPrestacionNueva.setMedico(medicoExistente);
        medicoPrestacionNueva.setPrestacion(prestacionExistente);
        MedicoPrestacion medicoPrestacionGuardada = medicoPrestacionDomainService.saveMedicoPrestacion(medicoPrestacionNueva);

        //Devolver response mapeado
        GetMedicoPrestacionResponse getMedicoPrestacionResponse = medicoPrestacionMapper.toGetResponse(medicoPrestacionGuardada);
        return getMedicoPrestacionResponse;

    }

    /**
     * Desasigna una prestación de un médico (baja lógica de la asignación).
     *
     * @param id {@code UUID} identificador de la asignación
     * @return {@code SoftDeleteMedicoPrestacionResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la asignación no existe
     */
    @Transactional
    public SoftDeleteMedicoPrestacionResponse unassignPrestacion(UUID id) {

        log.info("Desasignación de prestación de médico iniciada: id={}", id);

        //Buscar la asignación activa
        MedicoPrestacion medicoPrestacionExistente = medicoPrestacionDomainService.findMedicoPrestacionActivaById(id);

        //Dar de baja
        medicoPrestacionDomainService.softDeleteMedicoPrestacion(medicoPrestacionExistente, "Desasignación de prestación");

        //Devolver response mapeado
        SoftDeleteMedicoPrestacionResponse softDeleteMedicoPrestacionResponse =
                medicoPrestacionMapper.toSoftDeleteResponse(medicoPrestacionExistente);
        return softDeleteMedicoPrestacionResponse;

    }

    //endregion

}
