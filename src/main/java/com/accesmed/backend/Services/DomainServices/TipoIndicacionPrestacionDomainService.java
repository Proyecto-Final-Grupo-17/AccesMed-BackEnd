package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Repositories.TipoIndicacionPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code TipoIndicacionPrestacion}.
 * Encapsula las operaciones de guardar, buscar y validaciones de reglas de negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TipoIndicacionPrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionRepository tipoIndicacionPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un tipo de indicación de prestación en la base de datos.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} entidad a persistir
     * @return {@code TipoIndicacionPrestacion} el tipo guardado
     */
    public TipoIndicacionPrestacion saveTipoIndicacionPrestacion(TipoIndicacionPrestacion tipoIndicacionPrestacion) {

        log.debug("Guardando tipo de indicación de prestación: código={}", tipoIndicacionPrestacion.getCodigo());

        return tipoIndicacionPrestacionRepository.save(tipoIndicacionPrestacion);

    }

    /**
     * Busca un tipo de indicación de prestación activo por su identificador.
     *
     * @param id {@code UUID} identificador del tipo de indicación
     * @return {@code TipoIndicacionPrestacion} el tipo activo correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         tipo activo con ese id
     */
    public TipoIndicacionPrestacion findTipoIndicacionPrestacionActivoById(UUID id) {

        log.debug("Buscando tipo de indicación de prestación activo por id: {}", id);

        return tipoIndicacionPrestacionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el tipo de indicación de prestación: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "TIPO_INDICACION_PRESTACION_NO_ENCONTRADO",
                            "No existe un tipo de indicación de prestación activo con el id " + id);
                });

    }

    /**
     * Valida que el código de tipo de indicación sea único entre los tipos activos.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un tipo activo
     *         con ese código
     */
    public void validateCodigoTipoIndicacionPrestacionIsUnique(String codigo) {

        if (tipoIndicacionPrestacionRepository.existsByCodigoAndDeletedAtIsNull(codigo)) {
            log.warn("No se pudo crear el tipo de indicación: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "TIPO_INDICACION_PRESTACION_CODIGO_DUPLICADO",
                    "Ya existe un tipo de indicación de prestación activo con el código " + codigo);
        }

    }

    /**
     * Valida que el código de tipo de indicación sea único, excluyendo un id concreto.
     * Útil para la actualización.
     *
     * @param codigo {@code String} código a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro tipo activo
     *         con ese código
     */
    public void validateCodigoTipoIndicacionPrestacionIsUnique(String codigo, UUID idExcluido) {

        if (tipoIndicacionPrestacionRepository.existsByCodigoAndDeletedAtIsNullAndIdNot(codigo, idExcluido)) {
            log.warn("No se pudo actualizar el tipo de indicación: código {} ya existe en otro tipo", codigo);
            throw new ReglaNegocioException(getClass(), "TIPO_INDICACION_PRESTACION_CODIGO_DUPLICADO",
                    "Ya existe otro tipo de indicación de prestación activo con el código " + codigo);
        }

    }

    /**
     * Valida que el nombre de tipo de indicación sea único entre los tipos activos.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un tipo activo
     *         con ese nombre
     */
    public void validateNombreTipoIndicacionPrestacionIsUnique(String nombre) {

        if (tipoIndicacionPrestacionRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            log.warn("No se pudo crear el tipo de indicación: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "TIPO_INDICACION_PRESTACION_NOMBRE_DUPLICADO",
                    "Ya existe un tipo de indicación de prestación activo con el nombre " + nombre);
        }

    }

    /**
     * Valida que el nombre de tipo de indicación sea único, excluyendo un id concreto.
     * Útil para la actualización.
     *
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro tipo activo
     *         con ese nombre
     */
    public void validateNombreTipoIndicacionPrestacionIsUnique(String nombre, UUID idExcluido) {

        if (tipoIndicacionPrestacionRepository.existsByNombreAndDeletedAtIsNullAndIdNot(nombre, idExcluido)) {
            log.warn("No se pudo actualizar el tipo de indicación: nombre {} ya existe en otro tipo", nombre);
            throw new ReglaNegocioException(getClass(), "TIPO_INDICACION_PRESTACION_NOMBRE_DUPLICADO",
                    "Ya existe otro tipo de indicación de prestación activo con el nombre " + nombre);
        }

    }

    /**
     * Realiza la baja lógica de un tipo de indicación de prestación.
     *
     * @param tipoIndicacionPrestacion {@code TipoIndicacionPrestacion} tipo a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteTipoIndicacionPrestacion(TipoIndicacionPrestacion tipoIndicacionPrestacion, String motivo) {

        log.debug("Dando de baja tipo de indicación de prestación: código={}, motivo={}",
                tipoIndicacionPrestacion.getCodigo(), motivo);

        tipoIndicacionPrestacion.setDeletedAt(Instant.now());
        tipoIndicacionPrestacion.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveTipoIndicacionPrestacion(tipoIndicacionPrestacion);

    }

    //endregion

}
