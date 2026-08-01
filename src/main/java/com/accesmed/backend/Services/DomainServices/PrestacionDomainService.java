package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.PrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Prestacion}.
 * Encapsula las operaciones de guardar, buscar y validaciones de reglas de negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionRepository prestacionRepository;

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
     * Busca una prestación activa por su identificador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code Prestacion} la prestación activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         prestación activa con ese id
     */
    public Prestacion findActivePrestacionById(UUID id) {

        log.debug("Buscando prestación por id: {}", id);

        return prestacionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la prestación: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                            "No existe una prestación activa con el id " + id);
                });

    }

    /**
     * Valida que el código de prestación sea único entre las prestaciones activas.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación
     *         activa con ese código
     */
    public void validateCodigoPrestacionIsUnique(String codigo) {

        if (prestacionRepository.existsByCodigoAndDeletedAtIsNull(codigo)) {
            log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
                    "Ya existe una prestación activa con el código " + codigo);
        }

    }

    /**
     * Valida que el nombre de prestación sea único entre las prestaciones activas.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación
     *         activa con ese nombre
     */
    public void validateNombrePrestacionIsUnique(String nombre) {

        if (prestacionRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            log.warn("No se pudo crear la prestación: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NOMBRE_DUPLICADO",
                    "Ya existe una prestación activa con el nombre " + nombre);
        }

    }

    /**
     * Valida que el nombre de prestación sea único, excluyendo un id concreto.
     * Útil para la actualización.
     *
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra prestación
     *         activa con ese nombre
     */
    public void validateNombrePrestacionIsUnique(String nombre, UUID idExcluido) {

        if (prestacionRepository.existsByNombreAndDeletedAtIsNullAndIdNot(nombre, idExcluido)) {
            log.warn("No se pudo actualizar la prestación: nombre {} ya existe en otra prestación", nombre);
            throw new ReglaNegocioException(getClass(), "PRESTACION_NOMBRE_DUPLICADO",
                    "Ya existe otra prestación activa con el nombre " + nombre);
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

        // Null-checks para evitar NullPointerException
        if (duracionMinimaMinutos == null) {
            errores.add("La duración mínima es obligatoria.");
        } else if (duracionMinimaMinutos <= 0) {
            errores.add("La duración mínima debe ser mayor a cero.");
        }

        if (duracionMaximaMinutos == null) {
            errores.add("La duración máxima es obligatoria.");
        }

        // Si no hay errores en duraciones base, validar la relación
        if (duracionMinimaMinutos != null && duracionMaximaMinutos != null) {
            if (duracionMinimaMinutos > duracionMaximaMinutos) {
                errores.add("La duración mínima no puede superar a la duración máxima.");
            }
        }

        // Validar tolerancias en cascada
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

        // Validar recordatorio respecto a confirmación y solicitud
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

        // Lanzar si hay errores acumulados
        if (!errores.isEmpty()) {
            log.warn("Validación de reglas de tolerancia de prestación fallida: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    /**
     * Valida que la prestación esté en estado borrador (no habilitada).
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación ya está habilitada
     */
    public void validatePrestacionIsBorrador(Prestacion prestacion) {

        if (prestacion.getFechaHabilitacion() != null) {
            log.warn("La prestación {} ya está habilitada", prestacion.getCodigo());
            throw new ReglaNegocioException(getClass(), "PRESTACION_YA_HABILITADA",
                    "La prestación " + prestacion.getCodigo() + " ya está habilitada y no admite esta operación.");
        }

    }

    /**
     * Valida que la prestación esté habilitada. Es precondición para asociarle
     * {@code AgendaHorarios} o para pedir un {@code Turno}: una prestación en borrador
     * puede asignarse a un {@code Medico}, pero no ofrecerse para dar ni pedir turnos.
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación está en borrador
     */
    public void validatePrestacionIsHabilitada(Prestacion prestacion) {

        if (prestacion.getFechaHabilitacion() == null) {
            log.warn("La prestación {} no está habilitada", prestacion.getCodigo());
            throw new ReglaNegocioException(getClass(), "PRESTACION_NO_HABILITADA",
                    "La prestación " + prestacion.getCodigo() + " está en borrador y no admite esta operación.");
        }

    }

    /**
     * Valida que la prestación admita edición de indicaciones (debe estar en borrador).
     *
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la prestación está habilitada
     */
    public void validatePrestacionAdmiteEdicionDeIndicaciones(Prestacion prestacion) {

        if (prestacion.getFechaHabilitacion() != null) {
            log.warn("Intento de editar indicaciones de prestación habilitada: {}", prestacion.getCodigo());
            throw new ReglaNegocioException(getClass(), "INDICACION_PRESTACION_HABILITADA",
                    "Las indicaciones de una prestación habilitada no se pueden modificar.");
        }

    }

    /**
     * Habilita una prestación, irreversiblemente.
     *
     * @param prestacion {@code Prestacion} prestación a habilitar
     * @return {@code Prestacion} la prestación habilitada
     */
    public Prestacion habilitarPrestacion(Prestacion prestacion) {

        log.debug("Habilitando prestación: código={}", prestacion.getCodigo());

        prestacion.setFechaHabilitacion(ZonedDateTime.now());

        return savePrestacion(prestacion);

    }

    /**
     * Realiza la baja lógica de una prestación.
     *
     * @param prestacion {@code Prestacion} prestación a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeletePrestacion(Prestacion prestacion, String motivo) {

        log.debug("Dando de baja prestación: código={}, motivo={}", prestacion.getCodigo(), motivo);

        prestacion.setDeletedAt(Instant.now());
        prestacion.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        savePrestacion(prestacion);

    }

    //endregion

}
