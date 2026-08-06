package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Repositories.PlanRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Plan}.
 * Encapsula guardar, buscar y validaciones de unicidad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PlanRepository planRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un plan en la base de datos.
     *
     * @param plan {@code Plan} entidad a persistir
     * @return {@code Plan} el plan guardado
     */
    public Plan savePlan(Plan plan) {

        log.debug("Guardando plan: código={}", plan.getCodigo());

        return planRepository.save(plan);

    }

    /**
     * Busca un plan por su identificador.
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code Plan} el plan correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         un plan con ese id
     */
    public Plan findPlanById(UUID id) {

        log.debug("Buscando plan por id: {}", id);

        return planRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el plan: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "PLAN_NO_ENCONTRADO",
                            "No existe un plan con el id " + id);
                });

    }

    /**
     * Busca un plan activo (no deshabilitado) por su identificador. Deshabilitado es
     * terminal e irreversible, así que un plan en ese estado se trata como no disponible
     * para más operaciones.
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code Plan} el plan correspondiente al id, si no está deshabilitado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         un plan activo con ese id
     */
    public Plan findPlanActivoById(UUID id) {

        log.debug("Buscando plan activo por id: {}", id);

        return planRepository.findByIdAndEstadoActualNot(id, EstadoPlan.DESHABILITADO)
                .orElseThrow(() -> {
                    log.warn("No se encontró el plan activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "PLAN_NO_ENCONTRADO",
                            "No existe un plan activo con el id " + id);
                });

    }

    /**
     * Valida que el código del plan sea único entre los planes no deshabilitados de la
     * obra social.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un plan no
     *         deshabilitado con ese código en la obra social
     */
    public void validateCodigoPlanIsUnique(UUID obraSocialId, String codigo) {

        if (planRepository.existsByObraSocialIdAndCodigoAndEstadoActualNot(obraSocialId, codigo, EstadoPlan.DESHABILITADO)) {
            log.warn("No se pudo crear el plan: código {} ya existe en la obra social {}", codigo, obraSocialId);
            throw new ReglaNegocioException(getClass(), "PLAN_CODIGO_DUPLICADO",
                    "Ya existe un plan no deshabilitado con el código " + codigo + " en esta obra social.");
        }

    }

    /**
     * Valida que el nombre del plan sea único entre los planes no deshabilitados de la
     * obra social.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un plan no
     *         deshabilitado con ese nombre en la obra social
     */
    public void validateNombrePlanIsUnique(UUID obraSocialId, String nombre) {

        if (planRepository.existsByObraSocialIdAndNombreAndEstadoActualNot(obraSocialId, nombre, EstadoPlan.DESHABILITADO)) {
            log.warn("No se pudo crear el plan: nombre {} ya existe en la obra social {}", nombre, obraSocialId);
            throw new ReglaNegocioException(getClass(), "PLAN_NOMBRE_DUPLICADO",
                    "Ya existe un plan no deshabilitado con el nombre " + nombre + " en esta obra social.");
        }

    }

    /**
     * Valida que el nombre del plan sea único entre los no deshabilitados de la obra
     * social, excluyendo un id concreto. Útil para la actualización.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro plan no
     *         deshabilitado con ese nombre en la obra social
     */
    public void validateNombrePlanIsUnique(UUID obraSocialId, String nombre, UUID idExcluido) {

        if (planRepository.existsByObraSocialIdAndNombreAndEstadoActualNotAndIdNot(obraSocialId, nombre, EstadoPlan.DESHABILITADO, idExcluido)) {
            log.warn("No se pudo actualizar el plan: nombre {} ya existe en otro plan de la obra social {}", nombre, obraSocialId);
            throw new ReglaNegocioException(getClass(), "PLAN_NOMBRE_DUPLICADO",
                    "Ya existe otro plan no deshabilitado con el nombre " + nombre + " en esta obra social.");
        }

    }

    //endregion

}
