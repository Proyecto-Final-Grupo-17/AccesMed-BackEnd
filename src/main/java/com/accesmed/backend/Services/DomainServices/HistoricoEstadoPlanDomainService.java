package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Repositories.HistoricoEstadoPlanRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de dominio y persistencia para la entidad {@code HistoricoEstadoPlan}.
 * Encapsula la máquina de estados de {@code Plan}
 * ({@code No Publicado ⇄ Publicado → Deshabilitado}), tocando únicamente su propio
 * repositorio. Sus métodos siempre se invocan dentro de un método {@code @Transactional}
 * del caso de uso (capa {@code Application}), que es el límite real de atomicidad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricoEstadoPlanDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final HistoricoEstadoPlanRepository historicoEstadoPlanRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Obtiene el estado vigente de un plan desde su histórico: el estado del tramo con
     * {@code fechaHoraFin} vacío. Es la única fuente del estado actual (ya no se cachea en
     * la entidad); se usa para alimentar los mappers en las lecturas puntuales.
     *
     * @param planId {@code UUID} identificador del plan
     * @return {@code EstadoPlan} estado vigente del plan
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el plan
     *         no tiene un tramo de estado vigente (no debería pasar: todo plan abre su
     *         tramo inicial al crearse)
     */
    public EstadoPlan getEstadoVigente(UUID planId) {

        log.debug("Buscando estado vigente de plan: id={}", planId);

        return historicoEstadoPlanRepository.findByPlanIdAndFechaHoraFinIsNull(planId)
                .orElseThrow(() -> {
                    log.warn("No se encontró tramo de estado vigente para el plan: id={}", planId);
                    return new RecursoNoEncontradoException(getClass(), "PLAN_SIN_ESTADO_VIGENTE",
                            "No existe un tramo de estado vigente para el plan " + planId);
                })
                .getEstado();

    }

    /**
     * Obtiene el estado vigente de un conjunto de planes en una única consulta, armando un
     * {@code Map<UUID, EstadoPlan>} (id de plan → estado). Pensado para alimentar el estado
     * de toda una página de planes (o de los planes anidados de una obra social) sin
     * incurrir en N+1.
     *
     * @param planIds {@code Collection<UUID>} identificadores de los planes
     * @return {@code Map<UUID, EstadoPlan>} estado vigente por id de plan
     */
    public Map<UUID, EstadoPlan> getEstadosVigentes(Collection<UUID> planIds) {

        if (planIds.isEmpty()) {
            return Map.of();
        }

        log.debug("Buscando estados vigentes de {} plan(es)", planIds.size());

        return historicoEstadoPlanRepository.findByPlanIdInAndFechaHoraFinIsNull(planIds).stream()
                .collect(Collectors.toMap(historico -> historico.getPlan().getId(), HistoricoEstadoPlan::getEstado));

    }

    /**
     * Abre el primer tramo del histórico de estados de un plan recién persistido
     * ({@code NO_PUBLICADO}).
     *
     * @param planGuardado {@code Plan} plan ya persistido
     */
    public void openHistoricoInicialPlan(Plan planGuardado) {

        log.debug("Abriendo tramo inicial de estado para plan: código={}", planGuardado.getCodigo());

        //Crear nuevo histórico de estado con estado NO_PUBLICADO y fecha de inicio actual
        HistoricoEstadoPlan historicoEstadoPlanNuevo = new HistoricoEstadoPlan();
        historicoEstadoPlanNuevo.setPlan(planGuardado);
        historicoEstadoPlanNuevo.setEstado(EstadoPlan.NO_PUBLICADO);
        historicoEstadoPlanNuevo.setFechaHoraInicio(ZonedDateTime.now());

        historicoEstadoPlanRepository.save(historicoEstadoPlanNuevo);

    }

    /**
     * Cierra el tramo vigente del histórico de estados del plan y abre uno nuevo con el
     * estado destino — el histórico es la única fuente del estado, así que la transición
     * se resuelve por completo acá, sin ninguna caché que mantener. El {@code Plan} se
     * obtiene a través de la relación del propio tramo, sin llamar a
     * {@code PlanDomainService}.
     *
     * @param planId {@code UUID} identificador del plan a transicionar
     * @param estadoNuevo {@code EstadoPlan} estado destino de la transición
     * @param motivo {@code String} motivo de la transición, opcional
     * @return {@code Plan} el plan transicionado
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el plan ya está
     *         deshabilitado o si la transición no es válida desde el estado vigente
     */
    public Plan changeEstadoPlan(UUID planId, EstadoPlan estadoNuevo, String motivo) {

        //Buscar el tramo vigente no deshabilitado (si no existe, ya está deshabilitado)
        HistoricoEstadoPlan historicoEstadoPlanVigente = historicoEstadoPlanRepository
                .findByPlanIdAndFechaHoraFinIsNullAndEstadoNot(planId, EstadoPlan.DESHABILITADO)
                .orElseThrow(() -> {
                    log.warn("No se pudo transicionar el plan {}: ya está deshabilitado", planId);
                    return new ReglaNegocioException(getClass(), "PLAN_YA_DESHABILITADO",
                            "El plan ya está deshabilitado.");
                });

        //Obtener el Plan vía la relación del histórico
        Plan plan = historicoEstadoPlanVigente.getPlan();
        EstadoPlan estadoVigente = historicoEstadoPlanVigente.getEstado();

        log.debug("Transición de estado de plan: código={}, {} -> {}", plan.getCodigo(), estadoVigente, estadoNuevo);

        //Validar la transición según el estado vigente
        if (estadoNuevo == EstadoPlan.PUBLICADO && estadoVigente != EstadoPlan.NO_PUBLICADO) {
            log.warn("No se pudo publicar el plan {}: estado actual {}", plan.getCodigo(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "PLAN_NO_PUBLICABLE",
                    "El plan " + plan.getCodigo() + " no se puede publicar desde el estado " + estadoVigente + ".");
        }

        if (estadoNuevo == EstadoPlan.NO_PUBLICADO && estadoVigente != EstadoPlan.PUBLICADO) {
            log.warn("No se pudo despublicar el plan {}: estado actual {}", plan.getCodigo(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "PLAN_NO_DESPUBLICABLE",
                    "El plan " + plan.getCodigo() + " no se puede despublicar desde el estado " + estadoVigente + ".");
        }

        //Cerrar tramo vigente. saveAndFlush (no save): Hibernate agrupa las acciones del
        //flush por tipo y ejecuta todos los INSERT antes que los UPDATE sin importar el
        //orden del código, así que sin forzar el flush acá el INSERT del tramo nuevo
        //(fecha_hora_fin null) se ejecutaría antes que este UPDATE, violando por un
        //instante el índice único parcial (como máximo un tramo vigente por plan)
        historicoEstadoPlanVigente.setFechaHoraFin(ZonedDateTime.now());
        historicoEstadoPlanRepository.saveAndFlush(historicoEstadoPlanVigente);

        //Abrir tramo nuevo
        HistoricoEstadoPlan historicoEstadoPlanNuevo = new HistoricoEstadoPlan();
        historicoEstadoPlanNuevo.setPlan(plan);
        historicoEstadoPlanNuevo.setEstado(estadoNuevo);
        historicoEstadoPlanNuevo.setFechaHoraInicio(ZonedDateTime.now());
        historicoEstadoPlanNuevo.setMotivo(motivo);
        historicoEstadoPlanRepository.save(historicoEstadoPlanNuevo);

        return plan;

    }

    //endregion

}
