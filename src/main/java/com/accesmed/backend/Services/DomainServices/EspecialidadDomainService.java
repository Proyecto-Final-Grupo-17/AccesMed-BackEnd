package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Repositories.EspecialidadRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Especialidad}.
 * Encapsula guardar, buscar, validaciones de unicidad y la baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspecialidadDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadRepository especialidadRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una especialidad en la base de datos.
     *
     * @param especialidad {@code Especialidad} entidad a persistir
     * @return {@code Especialidad} la especialidad guardada
     */
    public Especialidad saveEspecialidad(Especialidad especialidad) {

        log.debug("Guardando especialidad: código={}", especialidad.getCodigo());

        return especialidadRepository.save(especialidad);

    }

    /**
     * Busca una especialidad activa por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code Especialidad} la especialidad activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         especialidad activa con ese id
     */
    public Especialidad findEspecialidadActivaById(UUID id) {

        log.debug("Buscando especialidad activa por id: {}", id);

        return especialidadRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la especialidad activa: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                            "No se encontró la especialidad solicitada. Es posible que haya sido dada de baja.");
                });

    }

    /**
     * Valida que el código de especialidad sea único entre las especialidades activas.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una especialidad
     *         activa con ese código
     */
    public void validateCodigoEspecialidadIsUnique(String codigo) {

        if (especialidadRepository.existsByCodigoAndDeletedAtIsNull(codigo)) {
            log.warn("No se pudo crear la especialidad: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CODIGO_DUPLICADO",
                    "Ya existe una especialidad con el código " + codigo + ".");
        }

    }

    /**
     * Valida que el código de especialidad sea único entre las activas, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param codigo {@code String} código a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra especialidad
     *         activa con ese código
     */
    public void validateCodigoEspecialidadIsUnique(String codigo, UUID idExcluido) {

        if (especialidadRepository.existsByCodigoAndDeletedAtIsNullAndIdNot(codigo, idExcluido)) {
            log.warn("No se pudo actualizar la especialidad: código {} ya existe en otra especialidad", codigo);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CODIGO_DUPLICADO",
                    "Ya existe otra especialidad con el código " + codigo + ".");
        }

    }

    /**
     * Valida que el nombre de especialidad sea único entre las especialidades activas.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una especialidad
     *         activa con ese nombre
     */
    public void validateNombreEspecialidadIsUnique(String nombre) {

        if (especialidadRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            log.warn("No se pudo crear la especialidad: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_NOMBRE_DUPLICADO",
                    "Ya existe una especialidad con el nombre " + nombre + ".");
        }

    }

    /**
     * Valida que el nombre de especialidad sea único entre las activas, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra especialidad
     *         activa con ese nombre
     */
    public void validateNombreEspecialidadIsUnique(String nombre, UUID idExcluido) {

        if (especialidadRepository.existsByNombreAndDeletedAtIsNullAndIdNot(nombre, idExcluido)) {
            log.warn("No se pudo actualizar la especialidad: nombre {} ya existe en otra especialidad", nombre);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_NOMBRE_DUPLICADO",
                    "Ya existe otra especialidad con el nombre " + nombre + ".");
        }

    }

    /**
     * Realiza la baja lógica de una especialidad.
     *
     * @param especialidad {@code Especialidad} especialidad a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteEspecialidad(Especialidad especialidad, String motivo) {

        log.debug("Dando de baja especialidad: código={}, motivo={}", especialidad.getCodigo(), motivo);

        especialidad.setDeletedAt(Instant.now());
        especialidad.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveEspecialidad(especialidad);

    }

    //endregion

}
