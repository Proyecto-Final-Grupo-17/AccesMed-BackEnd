package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.HistoricoEstadoPrestacionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@Slf4j
@AllArgsConstructor
public class HistoricoEstadoPrestacionDomainService {


    private final HistoricoEstadoPrestacionRepository historicoEstadoPrestacionRepository;
    private final PrestacionDomainService prestacionDomainService;

    /**
     * Abre el primer tramo del histórico de estados de una prestación recién creada
     * ({@code NO_PUBLICADA}), y setea el {@code estadoActual}.
     *
     * @param prestacion {@code Prestacion} prestación recién persistida
     * @return {@code HistoricoEstadoPrestacion} el tramo abierto
     */
    @Transactional
    public HistoricoEstadoPrestacion setInitialEstadoForNewPrestacion(Prestacion prestacion) {

        log.debug("Abriendo tramo inicial de estado para prestación: código={}", prestacion.getCodigo());

        //Crear nuevo histórico de estado con estado NO_PUBLICADA y fecha de inicio actual
        HistoricoEstadoPrestacion historicoEstadoPrestacionNuevo = new HistoricoEstadoPrestacion();
        historicoEstadoPrestacionNuevo.setPrestacion(prestacion);
        historicoEstadoPrestacionNuevo.setEstado(EstadoPrestacion.NO_PUBLICADA);
        historicoEstadoPrestacionNuevo.setFechaHoraInicio(ZonedDateTime.now());

        //Setear estado actual a la Prestacion
        prestacion.setEstadoActual(EstadoPrestacion.NO_PUBLICADA);

        return historicoEstadoPrestacionRepository.save(historicoEstadoPrestacionNuevo);

    }

    /**
     * Cierra el tramo vigente del histórico de estados de la prestación, abre uno nuevo
     * con el estado destino, y actualiza {@code prestacion.estadoActual} — cache e
     * histórico en la misma transacción.
     *
     * @param prestacion {@code Prestacion} prestación a transicionar
     * @param estadoNuevo {@code EstadoPrestacion} estado destino de la transición
     * @param motivo {@code String} motivo de la transición, opcional
     * @return {@code Prestacion} la prestación con el nuevo estado actual
     */
    public Prestacion changeEstadoPrestacion(Prestacion prestacion, EstadoPrestacion estadoNuevo, String motivo) {

        log.debug("Transición de estado de prestación: código={}, {} -> {}", prestacion.getCodigo(),
                prestacion.getEstadoActual(), estadoNuevo);

        // Validaciones de transición: delegar reglas de negocio a PrestacionDomainService
        if (estadoNuevo == EstadoPrestacion.PUBLICADA) {
            prestacionDomainService.validatePuedePublicar(prestacion);
        } else if (estadoNuevo == EstadoPrestacion.NO_PUBLICADA) {
            prestacionDomainService.validatePuedeDespublicar(prestacion);
        }

        ZonedDateTime ahora = ZonedDateTime.now();

        //Buscar instancia de EstadoPrestacion
        HistoricoEstadoPrestacion historicoEstadoPrestacionVigente = historicoEstadoPrestacionRepository
                .findByPrestacionIdAndFechaHoraFinIsNull(prestacion.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "La prestación " + prestacion.getCodigo() + " no tiene tramo de estado vigente."));
        historicoEstadoPrestacionVigente.setFechaHoraFin(ahora);
        historicoEstadoPrestacionRepository.save(historicoEstadoPrestacionVigente);

        //Crear nuevo HistoricoEstadoPrestacion
        HistoricoEstadoPrestacion historicoEstadoPrestacionNuevo = new HistoricoEstadoPrestacion();
        historicoEstadoPrestacionNuevo.setPrestacion(prestacion);
        historicoEstadoPrestacionNuevo.setEstado(estadoNuevo);
        historicoEstadoPrestacionNuevo.setFechaHoraInicio(ahora);
        historicoEstadoPrestacionNuevo.setMotivo(motivo);
        historicoEstadoPrestacionRepository.save(historicoEstadoPrestacionNuevo);

        //Actualizar estadoActual de la Prestacion
        prestacion.setEstadoActual(estadoNuevo);

        return prestacion;

    }


}
