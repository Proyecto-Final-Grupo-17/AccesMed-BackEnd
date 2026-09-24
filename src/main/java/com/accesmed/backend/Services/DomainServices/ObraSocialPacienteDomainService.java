package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.ObraSocialPaciente;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Repositories.ObraSocialPacienteRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code ObraSocialPaciente}.
 * Encapsula guardar, buscar, validaciones de reglas de negocio y la baja lógica de la
 * cobertura de obra social de un paciente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialPacienteDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPacienteRepository obraSocialPacienteRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una cobertura de obra social en la base de datos.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} entidad a persistir
     * @return {@code ObraSocialPaciente} la cobertura guardada
     */
    public ObraSocialPaciente saveObraSocialPaciente(ObraSocialPaciente obraSocialPaciente) {

        log.debug("Guardando cobertura de obra social: paciente={}, plan={}",
                obraSocialPaciente.getPaciente().getId(), obraSocialPaciente.getPlan().getId());

        return obraSocialPacienteRepository.save(obraSocialPaciente);

    }

    /**
     * Busca una cobertura de obra social activa por su identificador.
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code ObraSocialPaciente} la cobertura activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una cobertura activa con ese id
     */
    public ObraSocialPaciente findObraSocialPacienteActivaById(UUID id) {

        log.debug("Buscando cobertura de obra social activa por id: {}", id);

        return obraSocialPacienteRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la cobertura de obra social activa: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "OBRA_SOCIAL_PACIENTE_NO_ENCONTRADA",
                            "No se encontró la cobertura de obra social solicitada.");
                });

    }

    /**
     * Busca las coberturas activas de un paciente.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @return {@code List<ObraSocialPaciente>} las coberturas activas de ese paciente
     */
    public List<ObraSocialPaciente> findCoberturasActivasByPaciente(UUID pacienteId) {

        log.debug("Buscando coberturas activas del paciente: {}", pacienteId);

        return obraSocialPacienteRepository.findByPaciente_IdAndDeletedAtIsNull(pacienteId);

    }

    /**
     * Valida que no exista ya una cobertura activa entre el paciente y el plan indicados.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param planId {@code UUID} identificador del plan
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una
     *         cobertura activa entre ambos
     */
    public void validateSinCoberturaActiva(UUID pacienteId, UUID planId) {

        if (obraSocialPacienteRepository.existsByPaciente_IdAndPlan_IdAndDeletedAtIsNull(pacienteId, planId)) {
            log.warn("No se pudo asignar el plan {} al paciente {}: ya existe una cobertura activa", planId, pacienteId);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_PACIENTE_YA_ASIGNADA",
                    "El paciente ya tiene asignado ese plan.");
        }

    }

    /**
     * Valida que el plan pertenezca a la obra social indicada.
     *
     * @param plan {@code Plan} plan a validar
     * @param obraSocialId {@code UUID} identificador de la obra social esperada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plan no pertenece a esa obra social
     */
    public void validatePlanPerteneceAObraSocial(Plan plan, UUID obraSocialId) {

        if (!plan.getObraSocial().getId().equals(obraSocialId)) {
            log.warn("No se pudo asignar el plan {}: pertenece a la obra social {}, no a la obra social {}",
                    plan.getId(), plan.getObraSocial().getId(), obraSocialId);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_PACIENTE_PLAN_NO_PERTENECE",
                    "El plan seleccionado no pertenece a la obra social indicada.");
        }

    }

    /**
     * Realiza la baja lógica de una cobertura de obra social.
     *
     * @param obraSocialPaciente {@code ObraSocialPaciente} cobertura a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteObraSocialPaciente(ObraSocialPaciente obraSocialPaciente, String motivo) {

        log.debug("Dando de baja cobertura de obra social: id={}, motivo={}", obraSocialPaciente.getId(), motivo);

        obraSocialPaciente.setDeletedAt(Instant.now());
        obraSocialPaciente.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveObraSocialPaciente(obraSocialPaciente);

    }

    /**
     * Da de baja lógica todas las coberturas activas que referencian un plan. Utilizada
     * cuando se deshabilita el plan (A5) o, por transitividad, la obra social.
     *
     * @param planId {@code UUID} identificador del plan
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteByPlan(UUID planId, String motivo) {

        log.debug("Dando de baja las coberturas de obra social del plan: {}", planId);

        List<ObraSocialPaciente> coberturasActivas = obraSocialPacienteRepository.findByPlan_IdAndDeletedAtIsNull(planId);

        for (ObraSocialPaciente obraSocialPaciente : coberturasActivas) {
            softDeleteObraSocialPaciente(obraSocialPaciente, motivo);
        }

    }

    //endregion

}
