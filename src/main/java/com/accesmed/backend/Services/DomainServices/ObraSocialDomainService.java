package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Repositories.ObraSocialRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code ObraSocial}.
 * Encapsula guardar, buscar, validaciones de unicidad y la baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialRepository obraSocialRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una obra social en la base de datos.
     *
     * @param obraSocial {@code ObraSocial} entidad a persistir
     * @return {@code ObraSocial} la obra social guardada
     */
    public ObraSocial saveObraSocial(ObraSocial obraSocial) {

        log.debug("Guardando obra social: código={}", obraSocial.getCodigo());

        return obraSocialRepository.save(obraSocial);

    }

    /**
     * Busca una obra social activa por su identificador.
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code ObraSocial} la obra social activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         obra social activa con ese id
     */
    public ObraSocial findObraSocialById(UUID id) {

        log.debug("Buscando obra social por id: {}", id);

        return obraSocialRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la obra social: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "OBRA_SOCIAL_NO_ENCONTRADA",
                            "No existe una obra social activa con el id " + id);
                });

    }

    /**
     * Valida que el código de obra social sea único entre las activas.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una obra social
     *         activa con ese código
     */
    public void validateCodigoObraSocialIsUnique(String codigo) {

        if (obraSocialRepository.existsByCodigoAndDeletedAtIsNull(codigo)) {
            log.warn("No se pudo crear la obra social: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_CODIGO_DUPLICADO",
                    "Ya existe una obra social activa con el código " + codigo);
        }

    }

    /**
     * Valida que el código de obra social sea único entre las activas, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param codigo {@code String} código a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra obra social
     *         activa con ese código
     */
    public void validateCodigoObraSocialIsUnique(String codigo, UUID idExcluido) {

        if (obraSocialRepository.existsByCodigoAndDeletedAtIsNullAndIdNot(codigo, idExcluido)) {
            log.warn("No se pudo actualizar la obra social: código {} ya existe en otra obra social", codigo);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_CODIGO_DUPLICADO",
                    "Ya existe otra obra social activa con el código " + codigo);
        }

    }

    /**
     * Valida que el nombre de obra social sea único entre las activas.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una obra social
     *         activa con ese nombre
     */
    public void validateNombreObraSocialIsUnique(String nombre) {

        if (obraSocialRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            log.warn("No se pudo crear la obra social: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_NOMBRE_DUPLICADO",
                    "Ya existe una obra social activa con el nombre " + nombre);
        }

    }

    /**
     * Valida que el nombre de obra social sea único entre las activas, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra obra social
     *         activa con ese nombre
     */
    public void validateNombreObraSocialIsUnique(String nombre, UUID idExcluido) {

        if (obraSocialRepository.existsByNombreAndDeletedAtIsNullAndIdNot(nombre, idExcluido)) {
            log.warn("No se pudo actualizar la obra social: nombre {} ya existe en otra obra social", nombre);
            throw new ReglaNegocioException(getClass(), "OBRA_SOCIAL_NOMBRE_DUPLICADO",
                    "Ya existe otra obra social activa con el nombre " + nombre);
        }

    }

    /**
     * Realiza la baja lógica de una obra social.
     *
     * @param obraSocial {@code ObraSocial} obra social a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteObraSocial(ObraSocial obraSocial, String motivo) {

        log.debug("Dando de baja obra social: código={}, motivo={}", obraSocial.getCodigo(), motivo);

        obraSocial.setDeletedAt(Instant.now());
        obraSocial.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveObraSocial(obraSocial);

    }

    //endregion

}
