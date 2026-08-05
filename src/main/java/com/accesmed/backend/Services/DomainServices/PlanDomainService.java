package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Repositories.HistoricoEstadoPlanRepository;
import com.accesmed.backend.Repositories.PlanRepository;
import com.accesmed.backend.Repositories.TurnoRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Plan}.
 * Encapsula guardar, buscar, validaciones de reglas de negocio y la máquina de estados
 * ({@code No Publicado ⇄ Publicado → Deshabilitado}), espejo de {@code PrestacionDomainService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PlanRepository planRepository;
    private final HistoricoEstadoPlanRepository historicoEstadoPlanRepository;
    private final TurnoRepository turnoRepository;

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

    /**
     * Valida que el plan admita la transición a {@code PUBLICADO}: debe estar en
     * {@code NO_PUBLICADO}.
     *
     * @param plan {@code Plan} plan a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no está en {@code NO_PUBLICADO}
     */
    public void validatePuedePublicar(Plan plan) {

        if (plan.getEstadoActual() != EstadoPlan.NO_PUBLICADO) {
            log.warn("No se pudo publicar el plan {}: estado actual {}", plan.getCodigo(), plan.getEstadoActual());
            throw new ReglaNegocioException(getClass(), "PLAN_NO_PUBLICABLE",
                    "El plan " + plan.getCodigo() + " no se puede publicar desde el estado " + plan.getEstadoActual() + ".");
        }

    }

    /**
     * Valida que el plan admita la transición a {@code NO_PUBLICADO}: debe estar en
     * {@code PUBLICADO}.
     *
     * @param plan {@code Plan} plan a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no está en {@code PUBLICADO}
     */
    public void validatePuedeDespublicar(Plan plan) {

        if (plan.getEstadoActual() != EstadoPlan.PUBLICADO) {
            log.warn("No se pudo despublicar el plan {}: estado actual {}", plan.getCodigo(), plan.getEstadoActual());
            throw new ReglaNegocioException(getClass(), "PLAN_NO_DESPUBLICABLE",
                    "El plan " + plan.getCodigo() + " no se puede despublicar desde el estado " + plan.getEstadoActual() + ".");
        }

    }

    /**
     * Valida que el plan admita la transición a {@code DESHABILITADO}: no puede estar ya
     * deshabilitado (transición terminal, sin vuelta atrás).
     *
     * @param plan {@code Plan} plan a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya está deshabilitado
     */
    public void validatePuedeDeshabilitar(Plan plan) {

        if (plan.getEstadoActual() == EstadoPlan.DESHABILITADO) {
            log.warn("No se pudo deshabilitar el plan {}: ya está deshabilitado", plan.getCodigo());
            throw new ReglaNegocioException(getClass(), "PLAN_YA_DESHABILITADO",
                    "El plan " + plan.getCodigo() + " ya está deshabilitado.");
        }

    }

    /**
     * Valida que el plan no tenga turnos vivos (estado actual no final) cubiertos por él.
     * Única precondición de la baja restrictiva de deshabilitar: no rige la regla del
     * "último plan no deshabilitado de una obra social activa" (eliminada en v3).
     *
     * @param plan {@code Plan} plan a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos cubiertos por el plan
     */
    public void validateSinUsoVigente(Plan plan) {

        long turnosVivos = turnoRepository.countByObraSocialPaciente_Plan_IdAndEstadoActualNotIn(plan.getId(), EstadoTurno.FINALES);
        if (turnosVivos > 0) {
            ZonedDateTime fechaMaxima = turnoRepository
                    .findMaxFechaHoraInicioByPlanIdAndEstadoActualNotIn(plan.getId(), EstadoTurno.FINALES)
                    .orElse(null);
            log.warn("No se pudo deshabilitar el plan {}: {} turno(s) vivo(s), fecha máxima {}",
                    plan.getCodigo(), turnosVivos, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "PLAN_CON_TURNOS_VIVOS",
                    "El plan " + plan.getCodigo() + " tiene " + turnosVivos + " turno(s) vivo(s), el más lejano el "
                            + fechaMaxima + ". No se puede deshabilitar.");
        }

    }

    /**
     * Abre el primer tramo del histórico de estados de un plan recién agregado
     * ({@code NO_PUBLICADO}), y setea el {@code estadoActual}.
     *
     * @param plan {@code Plan} plan recién persistido
     * @return {@code HistoricoEstadoPlan} el tramo abierto
     */
    public HistoricoEstadoPlan abrirTramoInicial(Plan plan) {

        log.debug("Abriendo tramo inicial de estado para plan: código={}", plan.getCodigo());

        plan.setEstadoActual(EstadoPlan.NO_PUBLICADO);
        savePlan(plan);

        HistoricoEstadoPlan tramo = new HistoricoEstadoPlan();
        tramo.setPlan(plan);
        tramo.setEstado(EstadoPlan.NO_PUBLICADO);
        tramo.setFechaHoraInicio(ZonedDateTime.now());

        return historicoEstadoPlanRepository.save(tramo);

    }

    /**
     * Cierra el tramo vigente del histórico de estados del plan, abre uno nuevo con el
     * estado destino, y actualiza {@code plan.estadoActual} — cache e histórico en la
     * misma transacción.
     *
     * @param plan {@code Plan} plan a transicionar
     * @param estadoNuevo {@code EstadoPlan} estado destino de la transición
     * @param motivo {@code String} motivo de la transición, opcional
     * @return {@code Plan} el plan con el nuevo estado actual
     */
    public Plan abrirTramoEstado(Plan plan, EstadoPlan estadoNuevo, String motivo) {

        log.debug("Transición de estado de plan: código={}, {} -> {}", plan.getCodigo(), plan.getEstadoActual(), estadoNuevo);

        ZonedDateTime ahora = ZonedDateTime.now();

        HistoricoEstadoPlan tramoVigente = historicoEstadoPlanRepository
                .findByPlanIdAndFechaHoraFinIsNull(plan.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "El plan " + plan.getCodigo() + " no tiene tramo de estado vigente."));
        tramoVigente.setFechaHoraFin(ahora);
        historicoEstadoPlanRepository.save(tramoVigente);

        HistoricoEstadoPlan tramoNuevo = new HistoricoEstadoPlan();
        tramoNuevo.setPlan(plan);
        tramoNuevo.setEstado(estadoNuevo);
        tramoNuevo.setFechaHoraInicio(ahora);
        tramoNuevo.setMotivo(motivo);
        historicoEstadoPlanRepository.save(tramoNuevo);

        plan.setEstadoActual(estadoNuevo);

        return savePlan(plan);

    }

    //endregion

}
