package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Repositories.HistoricoEstadoPlanRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

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
     * Setea el {@code estadoActual} inicial ({@code NO_PUBLICADO}) de un plan recién
     * mapeado, antes de persistirlo. Hibernate captura los valores a insertar en el
     * momento del {@code persist()}: hay que llamar a este método antes de
     * {@code PlanDomainService.savePlan}, nunca después.
     *
     * @param plan {@code Plan} plan nuevo, todavía no persistido
     */
    public void seedEstadoInicialPlan(Plan plan) {

        plan.setEstadoActual(EstadoPlan.NO_PUBLICADO);

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
     * Cierra el tramo vigente del histórico de estados del plan, abre uno nuevo con el
     * estado destino, y actualiza {@code plan.estadoActual} — cache e histórico en la
     * misma transacción. El {@code Plan} se obtiene a través de la relación del propio
     * tramo, sin llamar a {@code PlanDomainService}: queda persistido por dirty checking
     * al cerrar la transacción abierta por el caso de uso que invoca este método.
     *
     * @param planId {@code UUID} identificador del plan a transicionar
     * @param estadoNuevo {@code EstadoPlan} estado destino de la transición
     * @param motivo {@code String} motivo de la transición, opcional
     * @return {@code Plan} el plan con el nuevo estado actual
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

        //Cerrar tramo vigente
        historicoEstadoPlanVigente.setFechaHoraFin(ZonedDateTime.now());
        historicoEstadoPlanRepository.save(historicoEstadoPlanVigente);

        //Abrir tramo nuevo
        HistoricoEstadoPlan historicoEstadoPlanNuevo = new HistoricoEstadoPlan();
        historicoEstadoPlanNuevo.setPlan(plan);
        historicoEstadoPlanNuevo.setEstado(estadoNuevo);
        historicoEstadoPlanNuevo.setFechaHoraInicio(ZonedDateTime.now());
        historicoEstadoPlanNuevo.setMotivo(motivo);
        historicoEstadoPlanRepository.save(historicoEstadoPlanNuevo);

        //Actualizar estadoActual en memoria (se persiste por dirty checking)
        plan.setEstadoActual(estadoNuevo);

        return plan;

    }

    //endregion

}
