package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.HistoricoEstadoPrestacionRepository;
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
 * Lógica de dominio y persistencia para la entidad {@code HistoricoEstadoPrestacion}.
 * Encapsula la máquina de estados de {@code Prestacion}
 * ({@code No Publicada ⇄ Publicada → Deshabilitada}), tocando únicamente su propio
 * repositorio. Sus métodos siempre se invocan dentro de un método {@code @Transactional}
 * del caso de uso (capa {@code Application}), que es el límite real de atomicidad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricoEstadoPrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final HistoricoEstadoPrestacionRepository historicoEstadoPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Obtiene el estado vigente de una prestación desde su histórico: el estado del tramo
     * con {@code fechaHoraFin} vacío. Es la única fuente del estado actual (ya no se
     * cachea en la entidad); se usa para alimentar los mappers en las lecturas puntuales.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code EstadoPrestacion} estado vigente de la prestación
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la
     *         prestación no tiene un tramo de estado vigente (no debería pasar: toda
     *         prestación abre su tramo inicial al crearse)
     */
    public EstadoPrestacion getEstadoVigente(UUID prestacionId) {

        log.debug("Buscando estado vigente de prestación: id={}", prestacionId);

        return historicoEstadoPrestacionRepository.findByPrestacionIdAndFechaHoraFinIsNull(prestacionId)
                .orElseThrow(() -> {
                    log.warn("No se encontró tramo de estado vigente para la prestación: id={}", prestacionId);
                    return new RecursoNoEncontradoException(getClass(), "PRESTACION_SIN_ESTADO_VIGENTE",
                            "No existe un tramo de estado vigente para la prestación " + prestacionId);
                })
                .getEstado();

    }

    /**
     * Obtiene el estado vigente de un conjunto de prestaciones en una única consulta,
     * armando un {@code Map<UUID, EstadoPrestacion>} (id de prestación → estado). Pensado
     * para alimentar el estado de toda una página de prestaciones sin incurrir en N+1.
     *
     * @param prestacionIds {@code Collection<UUID>} identificadores de las prestaciones
     * @return {@code Map<UUID, EstadoPrestacion>} estado vigente por id de prestación
     */
    public Map<UUID, EstadoPrestacion> getEstadosVigentes(Collection<UUID> prestacionIds) {

        if (prestacionIds.isEmpty()) {
            return Map.of();
        }

        log.debug("Buscando estados vigentes de {} prestación(es)", prestacionIds.size());

        return historicoEstadoPrestacionRepository.findByPrestacionIdInAndFechaHoraFinIsNull(prestacionIds).stream()
                .collect(Collectors.toMap(historico -> historico.getPrestacion().getId(), HistoricoEstadoPrestacion::getEstado));

    }

    /**
     * Abre el primer tramo del histórico de estados de una prestación recién persistida
     * ({@code NO_PUBLICADA}).
     *
     * @param prestacionGuardada {@code Prestacion} prestación ya persistida
     */
    public void openHistoricoInicialPrestacion(Prestacion prestacionGuardada) {

        log.debug("Abriendo tramo inicial de estado para prestación: código={}", prestacionGuardada.getCodigo());

        //Crear nuevo histórico de estado con estado NO_PUBLICADA y fecha de inicio actual
        HistoricoEstadoPrestacion historicoEstadoPrestacionNuevo = new HistoricoEstadoPrestacion();
        historicoEstadoPrestacionNuevo.setPrestacion(prestacionGuardada);
        historicoEstadoPrestacionNuevo.setEstado(EstadoPrestacion.NO_PUBLICADA);
        historicoEstadoPrestacionNuevo.setFechaHoraInicio(ZonedDateTime.now());

        historicoEstadoPrestacionRepository.save(historicoEstadoPrestacionNuevo);

    }

    /**
     * Cierra el tramo vigente del histórico de estados de la prestación y abre uno nuevo
     * con el estado destino — el histórico es la única fuente del estado, así que la
     * transición se resuelve por completo acá, sin ninguna caché que mantener. La
     * {@code Prestacion} se obtiene a través de la relación del propio tramo, sin llamar a
     * {@code PrestacionDomainService}.
     *
     * @param prestacionId {@code UUID} identificador de la prestación a transicionar
     * @param estadoNuevo {@code EstadoPrestacion} estado destino de la transición
     * @param motivo {@code String} motivo de la transición, opcional
     * @return {@code Prestacion} la prestación transicionada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación ya
     *         está deshabilitada o si la transición no es válida desde el estado vigente
     */
    public Prestacion changeEstadoPrestacion(UUID prestacionId, EstadoPrestacion estadoNuevo, String motivo) {

        //Buscar el tramo vigente no deshabilitado (si no existe, ya está deshabilitada)
        HistoricoEstadoPrestacion historicoEstadoPrestacionVigente = historicoEstadoPrestacionRepository
                .findByPrestacionIdAndFechaHoraFinIsNullAndEstadoNot(prestacionId, EstadoPrestacion.DESHABILITADA)
                .orElseThrow(() -> {
                    log.warn("No se pudo transicionar la prestación {}: ya está deshabilitada", prestacionId);
                    return new ReglaNegocioException(getClass(), "PRESTACION_YA_DESHABILITADA",
                            "La prestación ya está deshabilitada.");
                });

        //Obtener la Prestacion vía la relación del histórico
        Prestacion prestacion = historicoEstadoPrestacionVigente.getPrestacion();
        EstadoPrestacion estadoVigente = historicoEstadoPrestacionVigente.getEstado();

        log.debug("Transición de estado de prestación: código={}, {} -> {}", prestacion.getCodigo(),
                estadoVigente, estadoNuevo);

        //Validar la transición según el estado vigente
        if (estadoNuevo == EstadoPrestacion.PUBLICADA && estadoVigente != EstadoPrestacion.NO_PUBLICADA) {
            log.warn("No se pudo publicar la prestación {}: estado actual {}", prestacion.getCodigo(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_PUBLICABLE",
                    "La prestación " + prestacion.getCodigo() + " no se puede publicar desde el estado " + estadoVigente + ".");
        }

        if (estadoNuevo == EstadoPrestacion.NO_PUBLICADA && estadoVigente != EstadoPrestacion.PUBLICADA) {
            log.warn("No se pudo despublicar la prestación {}: estado actual {}", prestacion.getCodigo(), estadoVigente);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_DESPUBLICABLE",
                    "La prestación " + prestacion.getCodigo() + " no se puede despublicar desde el estado " + estadoVigente + ".");
        }

        //Cerrar tramo vigente. saveAndFlush (no save): Hibernate agrupa las acciones del
        //flush por tipo y ejecuta todos los INSERT antes que los UPDATE sin importar el
        //orden del código, así que sin forzar el flush acá el INSERT del tramo nuevo
        //(fecha_hora_fin null) se ejecutaría antes que este UPDATE, violando por un
        //instante el índice único parcial (como máximo un tramo vigente por prestación)
        historicoEstadoPrestacionVigente.setFechaHoraFin(ZonedDateTime.now());
        historicoEstadoPrestacionRepository.saveAndFlush(historicoEstadoPrestacionVigente);

        //Abrir tramo nuevo
        HistoricoEstadoPrestacion historicoEstadoPrestacionNuevo = new HistoricoEstadoPrestacion();
        historicoEstadoPrestacionNuevo.setPrestacion(prestacion);
        historicoEstadoPrestacionNuevo.setEstado(estadoNuevo);
        historicoEstadoPrestacionNuevo.setFechaHoraInicio(ZonedDateTime.now());
        historicoEstadoPrestacionNuevo.setMotivo(motivo);
        historicoEstadoPrestacionRepository.save(historicoEstadoPrestacionNuevo);

        return prestacion;

    }

    //endregion

}
