package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.HistoricoEstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.AgendaHorariosRepository;
import com.accesmed.backend.Repositories.HistoricoEstadoPrestacionRepository;
import com.accesmed.backend.Repositories.PrestacionRepository;
import com.accesmed.backend.Repositories.TurnoRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Prestacion}.
 * Encapsula guardar, buscar, validaciones de reglas de negocio y la máquina de estados
 * ({@code No Publicada ⇄ Publicada → Deshabilitada}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionRepository prestacionRepository;
    private final HistoricoEstadoPrestacionRepository historicoEstadoPrestacionRepository;
    private final TurnoRepository turnoRepository;
    private final AgendaHorariosRepository agendaHorariosRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una prestación en la base de datos.
     *
     * @param prestacion {@code Prestacion} entidad a persistir
     * @return {@code Prestacion} la prestación guardada
     */
    public Prestacion savePrestacion(Prestacion prestacion) {

        log.debug("Guardando prestación: código={}", prestacion.getCodigo());

        return prestacionRepository.save(prestacion);

    }

    /**
     * Busca una prestación por su identificador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code Prestacion} la prestación correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una prestación con ese id
     */
    public Prestacion findPrestacionById(UUID id) {

        log.debug("Buscando prestación por id: {}", id);

        return prestacionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la prestación: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                            "No existe una prestación con el id " + id);
                });

    }

    /**
     * Valida que el código de prestación sea único entre las prestaciones no deshabilitadas.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación
     *         no deshabilitada con ese código
     */
    public void validateCodigoPrestacionIsUnique(String codigo) {

        if (prestacionRepository.existsByCodigoAndEstadoActualNot(codigo, EstadoPrestacion.DESHABILITADA)) {
            log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
                    "Ya existe una prestación no deshabilitada con el código " + codigo);
        }

    }

    /**
     * Valida que el nombre de prestación sea único entre las prestaciones no deshabilitadas.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación
     *         no deshabilitada con ese nombre
     */
    public void validateNombrePrestacionIsUnique(String nombre) {

        if (prestacionRepository.existsByNombreAndEstadoActualNot(nombre, EstadoPrestacion.DESHABILITADA)) {
            log.warn("No se pudo crear la prestación: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NOMBRE_DUPLICADO",
                    "Ya existe una prestación no deshabilitada con el nombre " + nombre);
        }

    }

    /**
     * Valida que el nombre de prestación sea único entre las no deshabilitadas, excluyendo
     * un id concreto. Útil para la actualización.
     *
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra prestación
     *         no deshabilitada con ese nombre
     */
    public void validateNombrePrestacionIsUnique(String nombre, UUID idExcluido) {

        if (prestacionRepository.existsByNombreAndEstadoActualNotAndIdNot(nombre, EstadoPrestacion.DESHABILITADA, idExcluido)) {
            log.warn("No se pudo actualizar la prestación: nombre {} ya existe en otra prestación", nombre);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NOMBRE_DUPLICADO",
                    "Ya existe otra prestación no deshabilitada con el nombre " + nombre);
        }

    }

    /**
     * Valida las reglas de tolerancia de la prestación: relación entre todas las
     * tolerancias y duraciones. Acumula todos los errores en una lista y los devuelve
     * de una sola vez.
     *
     * @param duracionMinimaMinutos {@code Integer} duración mínima en minutos
     * @param duracionMaximaMinutos {@code Integer} duración máxima en minutos
     * @param solicitudMinutos {@code Integer} tolerancia de solicitud en minutos
     * @param validacionMinutos {@code Integer} tolerancia de validación en minutos
     * @param reprogramacionMinutos {@code Integer} tolerancia de reprogramación en minutos
     * @param confirmacionMinutos {@code Integer} tolerancia de confirmación en minutos
     * @param cancelacionMinutos {@code Integer} tolerancia de cancelación en minutos
     * @param anuncioMinutos {@code Integer} tolerancia de anuncio en minutos
     * @param recordatorioMinutos {@code Integer} recordatorio de confirmación en minutos
     * @throws ValidacionException {@code ValidacionException} si alguna de las validaciones falla
     */
    public void validateToleranciasPrestacion(Integer duracionMinimaMinutos, Integer duracionMaximaMinutos,
                                             Integer solicitudMinutos, Integer validacionMinutos,
                                             Integer reprogramacionMinutos, Integer confirmacionMinutos,
                                             Integer cancelacionMinutos, Integer anuncioMinutos,
                                             Integer recordatorioMinutos) {

        List<String> errores = new ArrayList<>();

        if (duracionMinimaMinutos == null) {
            errores.add("La duración mínima es obligatoria.");
        } else if (duracionMinimaMinutos <= 0) {
            errores.add("La duración mínima debe ser mayor a cero.");
        }

        if (duracionMaximaMinutos == null) {
            errores.add("La duración máxima es obligatoria.");
        }

        if (duracionMinimaMinutos != null && duracionMaximaMinutos != null) {
            if (duracionMinimaMinutos > duracionMaximaMinutos) {
                errores.add("La duración mínima no puede superar a la duración máxima.");
            }
        }

        if (solicitudMinutos != null && validacionMinutos != null) {
            if (solicitudMinutos < validacionMinutos) {
                errores.add("La tolerancia de solicitud no puede ser menor a la de validación.");
            }
        }

        if (validacionMinutos != null && reprogramacionMinutos != null) {
            if (validacionMinutos < reprogramacionMinutos) {
                errores.add("La tolerancia de validación no puede ser menor a la de reprogramación.");
            }
        }

        if (reprogramacionMinutos != null && confirmacionMinutos != null) {
            if (reprogramacionMinutos < confirmacionMinutos) {
                errores.add("La tolerancia de reprogramación no puede ser menor a la de confirmación.");
            }
        }

        if (confirmacionMinutos != null && cancelacionMinutos != null) {
            if (confirmacionMinutos < cancelacionMinutos) {
                errores.add("La tolerancia de confirmación no puede ser menor a la de cancelación.");
            }
        }

        if (cancelacionMinutos != null && cancelacionMinutos < 0) {
            errores.add("La tolerancia de cancelación no puede ser negativa.");
        }

        if (anuncioMinutos != null && anuncioMinutos < 0) {
            errores.add("La tolerancia de anuncio no puede ser negativa.");
        }

        if (recordatorioMinutos != null && confirmacionMinutos != null) {
            if (recordatorioMinutos <= confirmacionMinutos) {
                errores.add("El recordatorio de confirmación debe ser mayor a la tolerancia de confirmación.");
            }
        }

        if (recordatorioMinutos != null && solicitudMinutos != null) {
            if (recordatorioMinutos > solicitudMinutos) {
                errores.add("El recordatorio de confirmación no puede superar a la tolerancia de solicitud.");
            }
        }

        if (!errores.isEmpty()) {
            log.warn("Validación de reglas de tolerancia de prestación fallida: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    /**
     * Valida que la prestación admita la transición a {@code PUBLICADA}: debe estar en
     * {@code NO_PUBLICADA}.
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no está en {@code NO_PUBLICADA}
     */
    public void validatePuedePublicar(Prestacion prestacion) {

        if (prestacion.getEstadoActual() != EstadoPrestacion.NO_PUBLICADA) {
            log.warn("No se pudo publicar la prestación {}: estado actual {}", prestacion.getCodigo(), prestacion.getEstadoActual());
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_PUBLICABLE",
                    "La prestación " + prestacion.getCodigo() + " no se puede publicar desde el estado " + prestacion.getEstadoActual() + ".");
        }

    }

    /**
     * Valida que la prestación admita la transición a {@code NO_PUBLICADA}: debe estar en
     * {@code PUBLICADA}.
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si no está en {@code PUBLICADA}
     */
    public void validatePuedeDespublicar(Prestacion prestacion) {

        if (prestacion.getEstadoActual() != EstadoPrestacion.PUBLICADA) {
            log.warn("No se pudo despublicar la prestación {}: estado actual {}", prestacion.getCodigo(), prestacion.getEstadoActual());
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_DESPUBLICABLE",
                    "La prestación " + prestacion.getCodigo() + " no se puede despublicar desde el estado " + prestacion.getEstadoActual() + ".");
        }

    }

    /**
     * Valida que la prestación admita la transición a {@code DESHABILITADA}: no puede
     * estar ya deshabilitada (transición terminal, sin vuelta atrás).
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya está deshabilitada
     */
    public void validatePuedeDeshabilitar(Prestacion prestacion) {

        if (prestacion.getEstadoActual() == EstadoPrestacion.DESHABILITADA) {
            log.warn("No se pudo deshabilitar la prestación {}: ya está deshabilitada", prestacion.getCodigo());
            throw new ReglaNegocioException(getClass(), "PRESTACION_YA_DESHABILITADA",
                    "La prestación " + prestacion.getCodigo() + " ya está deshabilitada.");
        }

    }

    /**
     * Valida que la prestación no tenga turnos vivos (estado actual no final), y no
     * tenga horarios de agenda futuros ocupados. Precondición real de la baja
     * restrictiva de deshabilitar.
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay turnos vivos o
     *         agenda futura ocupada de esa prestación
     */
    public void validateSinUsoVigente(Prestacion prestacion) {

        long turnosVivos = turnoRepository.countByPrestacionIdAndEstadoActualNotIn(prestacion.getId(), EstadoTurno.FINALES);
        if (turnosVivos > 0) {
            ZonedDateTime fechaMaxima = turnoRepository
                    .findMaxFechaHoraInicioByPrestacionIdAndEstadoActualNotIn(prestacion.getId(), EstadoTurno.FINALES)
                    .orElse(null);
            log.warn("No se pudo deshabilitar la prestación {}: {} turno(s) vivo(s), fecha máxima {}",
                    prestacion.getCodigo(), turnosVivos, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CON_TURNOS_VIVOS",
                    "La prestación " + prestacion.getCodigo() + " tiene " + turnosVivos
                            + " turno(s) vivo(s), el más lejano el " + fechaMaxima + ". No se puede deshabilitar.");
        }

        if (agendaHorariosRepository.existsByPrestacionIdAndEstaOcupadaTrueAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(
                prestacion.getId(), LocalDate.now())) {
            log.warn("No se pudo deshabilitar la prestación {}: tiene horarios de agenda futuros ocupados", prestacion.getCodigo());
            throw new ReglaNegocioException(getClass(), "PRESTACION_CON_AGENDA_OCUPADA",
                    "La prestación " + prestacion.getCodigo() + " tiene horarios de agenda futuros ocupados. No se puede deshabilitar.");
        }

    }

    /**
     * Abre el primer tramo del histórico de estados de una prestación recién creada
     * ({@code NO_PUBLICADA}), y setea el {@code estadoActual}.
     *
     * @param prestacion {@code Prestacion} prestación recién persistida
     * @return {@code HistoricoEstadoPrestacion} el tramo abierto
     */
    public HistoricoEstadoPrestacion abrirTramoInicial(Prestacion prestacion) {

        log.debug("Abriendo tramo inicial de estado para prestación: código={}", prestacion.getCodigo());

        prestacion.setEstadoActual(EstadoPrestacion.NO_PUBLICADA);
        savePrestacion(prestacion);

        HistoricoEstadoPrestacion tramo = new HistoricoEstadoPrestacion();
        tramo.setPrestacion(prestacion);
        tramo.setEstado(EstadoPrestacion.NO_PUBLICADA);
        tramo.setFechaHoraInicio(ZonedDateTime.now());

        return historicoEstadoPrestacionRepository.save(tramo);

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
    public Prestacion abrirTramoEstado(Prestacion prestacion, EstadoPrestacion estadoNuevo, String motivo) {

        log.debug("Transición de estado de prestación: código={}, {} -> {}", prestacion.getCodigo(),
                prestacion.getEstadoActual(), estadoNuevo);

        ZonedDateTime ahora = ZonedDateTime.now();

        HistoricoEstadoPrestacion tramoVigente = historicoEstadoPrestacionRepository
                .findByPrestacionIdAndFechaHoraFinIsNull(prestacion.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "La prestación " + prestacion.getCodigo() + " no tiene tramo de estado vigente."));
        tramoVigente.setFechaHoraFin(ahora);
        historicoEstadoPrestacionRepository.save(tramoVigente);

        HistoricoEstadoPrestacion tramoNuevo = new HistoricoEstadoPrestacion();
        tramoNuevo.setPrestacion(prestacion);
        tramoNuevo.setEstado(estadoNuevo);
        tramoNuevo.setFechaHoraInicio(ahora);
        tramoNuevo.setMotivo(motivo);
        historicoEstadoPrestacionRepository.save(tramoNuevo);

        prestacion.setEstadoActual(estadoNuevo);

        return savePrestacion(prestacion);

    }

    //endregion

}
