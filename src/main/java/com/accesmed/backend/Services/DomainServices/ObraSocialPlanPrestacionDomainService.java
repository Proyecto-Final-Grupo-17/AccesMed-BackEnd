package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.ObraSocialPlanPrestacion;
import com.accesmed.backend.Repositories.ObraSocialPlanPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code ObraSocialPlanPrestacion}.
 * Encapsula guardar, buscar, validaciones de reglas de negocio y la baja lógica de la
 * cobertura de una prestación por un plan de obra social.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialPlanPrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPlanPrestacionRepository obraSocialPlanPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una cobertura de plan-prestación en la base de datos.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} entidad a persistir
     * @return {@code ObraSocialPlanPrestacion} la cobertura guardada
     */
    public ObraSocialPlanPrestacion saveObraSocialPlanPrestacion(ObraSocialPlanPrestacion obraSocialPlanPrestacion) {

        log.debug("Guardando cobertura plan-prestación: plan={}, prestación={}",
                obraSocialPlanPrestacion.getPlan().getId(), obraSocialPlanPrestacion.getPrestacion().getId());

        return obraSocialPlanPrestacionRepository.save(obraSocialPlanPrestacion);

    }

    /**
     * Busca una cobertura activa por su identificador.
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code ObraSocialPlanPrestacion} la cobertura activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una cobertura activa con ese id
     */
    public ObraSocialPlanPrestacion findObraSocialPlanPrestacionActivaById(UUID id) {

        log.debug("Buscando cobertura plan-prestación activa por id: {}", id);

        return obraSocialPlanPrestacionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la cobertura plan-prestación activa: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "OBRA_SOCIAL_PLAN_PRESTACION_NO_ENCONTRADA",
                            "No se encontró la cobertura solicitada.");
                });

    }

    /**
     * Busca las coberturas activas de un plan.
     *
     * @param planId {@code UUID} identificador del plan
     * @return {@code List<ObraSocialPlanPrestacion>} las coberturas activas de ese plan
     */
    public List<ObraSocialPlanPrestacion> findCoberturasActivasByPlan(UUID planId) {

        log.debug("Buscando coberturas activas del plan: {}", planId);

        return obraSocialPlanPrestacionRepository.findByPlan_IdAndDeletedAtIsNull(planId);

    }

    /**
     * Busca las coberturas activas de un lote de planes, en una sola consulta.
     *
     * @param planIds {@code Collection<UUID>} identificadores de los planes
     * @return {@code List<ObraSocialPlanPrestacion>} las coberturas activas de esos planes
     */
    public List<ObraSocialPlanPrestacion> findCoberturasActivasByPlanes(Collection<UUID> planIds) {

        if (planIds.isEmpty()) {
            return List.of();
        }

        log.debug("Buscando coberturas activas de los planes: {}", planIds);

        return obraSocialPlanPrestacionRepository.findByPlan_IdInAndDeletedAtIsNull(planIds);

    }

    /**
     * Valida que no exista ya una cobertura activa entre el plan y la prestación indicados.
     *
     * @param planId {@code UUID} identificador del plan
     * @param prestacionId {@code UUID} identificador de la prestación
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una
     *         cobertura activa entre ambos
     */
    public void validateSinCoberturaActiva(UUID planId, UUID prestacionId) {

        if (obraSocialPlanPrestacionRepository.existsByPlan_IdAndPrestacion_IdAndDeletedAtIsNull(planId, prestacionId)) {
            log.warn("No se pudo asignar la prestación {} al plan {}: ya existe una cobertura activa", prestacionId, planId);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_PLAN_PRESTACION_YA_ASIGNADA",
                    "El plan ya tiene asignada esa prestación.");
        }

    }

    /**
     * Realiza la baja lógica de una cobertura de plan-prestación.
     *
     * @param obraSocialPlanPrestacion {@code ObraSocialPlanPrestacion} cobertura a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteObraSocialPlanPrestacion(ObraSocialPlanPrestacion obraSocialPlanPrestacion, String motivo) {

        log.debug("Dando de baja cobertura plan-prestación: id={}, motivo={}", obraSocialPlanPrestacion.getId(), motivo);

        obraSocialPlanPrestacion.setDeletedAt(Instant.now());
        obraSocialPlanPrestacion.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveObraSocialPlanPrestacion(obraSocialPlanPrestacion);

    }

    /**
     * Da de baja lógica todas las coberturas activas de un plan. Utilizada cuando se
     * deshabilita el plan (A5, paso 1) o, por transitividad, la obra social.
     *
     * @param planId {@code UUID} identificador del plan
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteByPlan(UUID planId, String motivo) {

        log.debug("Dando de baja las coberturas del plan: {}", planId);

        List<ObraSocialPlanPrestacion> coberturasActivas = obraSocialPlanPrestacionRepository.findByPlan_IdAndDeletedAtIsNull(planId);

        for (ObraSocialPlanPrestacion cobertura : coberturasActivas) {
            softDeleteObraSocialPlanPrestacion(cobertura, motivo);
        }

    }

    /**
     * Da de baja lógica todas las coberturas activas de una prestación. Utilizada cuando
     * se deshabilita la prestación (A4, paso 4).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteByPrestacion(UUID prestacionId, String motivo) {

        log.debug("Dando de baja las coberturas de la prestación: {}", prestacionId);

        List<ObraSocialPlanPrestacion> coberturasActivas = obraSocialPlanPrestacionRepository.findByPrestacion_IdAndDeletedAtIsNull(prestacionId);

        for (ObraSocialPlanPrestacion cobertura : coberturasActivas) {
            softDeleteObraSocialPlanPrestacion(cobertura, motivo);
        }

    }

    /**
     * Busca la cobertura activa que existe entre un plan y una prestación indicados.
     * Valida que el plan del paciente cubre realmente la prestación.
     *
     * @param planId {@code UUID} identificador del plan
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code ObraSocialPlanPrestacion} la cobertura activa entre ambos
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una cobertura activa entre ese plan y esa prestación
     */
    public ObraSocialPlanPrestacion findCoberturaByPlanAndPrestacion(UUID planId, UUID prestacionId) {

        log.debug("Buscando cobertura entre plan={} y prestación={}", planId, prestacionId);

        return obraSocialPlanPrestacionRepository.findByPlan_IdAndPrestacion_IdAndDeletedAtIsNull(planId, prestacionId)
                .orElseThrow(() -> {
                    log.warn("No se encontró cobertura activa entre plan={} y prestación={}", planId, prestacionId);
                    return new RecursoNoEncontradoException(getClass(), "OBRA_SOCIAL_PLAN_PRESTACION_NO_ENCONTRADA",
                            "El plan no cubre esa prestación.");
                });

    }

    //endregion

}
