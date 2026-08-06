package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.PrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    //region ========== Dependencias  ==========

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
     * Busca una prestación activa (no deshabilitada) por su identificador. Deshabilitada
     * es terminal e irreversible, así que una prestación en ese estado se trata como no
     * disponible para más operaciones.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code Prestacion} la prestación correspondiente al id, si no está deshabilitada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una prestación activa con ese id
     */
    public Prestacion findPrestacionActivaById(UUID id) {

        log.debug("Buscando prestación activa por id: {}", id);

        return prestacionRepository.findByIdAndEstadoActualNot(id, EstadoPrestacion.DESHABILITADA)
                .orElseThrow(() -> {
                    log.warn("No se encontró la prestación activa: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                            "No existe una prestación activa con el id " + id);
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

        //Validar las duraciones (mínima > 0 y <= máxima)
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

        //Validar la cadena de tolerancias, descendente: solicitud >= validación >= reprogramación >= confirmación >= cancelación >= 0
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

        //Validar el anuncio (no depende de la cadena)
        if (anuncioMinutos != null && anuncioMinutos < 0) {
            errores.add("La tolerancia de anuncio no puede ser negativa.");
        }

        //Validar el recordatorio de confirmación (entre la tolerancia de confirmación y la de solicitud)
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

        //Lanzar si se acumuló algún error
        if (!errores.isEmpty()) {
            log.warn("Validación de reglas de tolerancia de prestación fallida: {}", errores);
            throw new ValidacionException(getClass(), errores);
        }

    }

    /**
     * Valida que una especialidad no tenga prestaciones no deshabilitadas asociadas.
     * Precondición real de la baja restrictiva de deshabilitar una especialidad.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay prestaciones no
     *         deshabilitadas de esa especialidad
     */
    public void validateSinPrestacionesActivas(UUID especialidadId) {

        if (prestacionRepository.existsByEspecialidadIdAndEstadoActualNot(especialidadId, EstadoPrestacion.DESHABILITADA)) {
            log.warn("No se pudo validar sin prestaciones activas para la especialidad {}: tiene prestaciones no deshabilitadas", especialidadId);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CON_PRESTACIONES_ACTIVAS",
                    "La especialidad " + especialidadId + " tiene prestaciones no deshabilitadas. No se puede dar de baja.");
        }

    }

    //endregion

}
