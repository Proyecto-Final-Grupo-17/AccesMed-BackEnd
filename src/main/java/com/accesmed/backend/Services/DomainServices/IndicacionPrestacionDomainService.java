package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Repositories.IndicacionPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code IndicacionPrestacion}.
 * Encapsula las operaciones de guardar, buscar y baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicacionPrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionRepository indicacionPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una indicación de prestación en la base de datos.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} entidad a persistir
     * @return {@code IndicacionPrestacion} la indicación guardada
     */
    public IndicacionPrestacion saveIndicacionPrestacion(IndicacionPrestacion indicacionPrestacion) {

        log.debug("Guardando indicación de prestación: nombre={}", indicacionPrestacion.getNombre());

        return indicacionPrestacionRepository.save(indicacionPrestacion);

    }

    /**
     * Guarda varias indicaciones de prestación en la base de datos.
     *
     * @param indicacionesPrestacion {@code List<IndicacionPrestacion>} lista de entidades a persistir
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones guardadas
     */
    public List<IndicacionPrestacion> saveIndicacionesPrestacion(List<IndicacionPrestacion> indicacionesPrestacion) {

        log.debug("Guardando {} indicaciones de prestación", indicacionesPrestacion.size());

        return indicacionPrestacionRepository.saveAll(indicacionesPrestacion);

    }

    /**
     * Busca una indicación de prestación activa por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code IndicacionPrestacion} la indicación activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         indicación activa con ese id
     */
    public IndicacionPrestacion findIndicacionPrestacionById(UUID id) {

        log.debug("Buscando indicación de prestación por id: {}", id);

        return indicacionPrestacionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la indicación de prestación: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "INDICACION_PRESTACION_NO_ENCONTRADA",
                            "No existe una indicación de prestación activa con el id " + id);
                });

    }

    /**
     * Busca todas las indicaciones de prestación activas asociadas a una prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones activas de esa prestación
     */
    public List<IndicacionPrestacion> findIndicacionesPrestacionByPrestacionId(UUID prestacionId) {

        log.debug("Buscando indicaciones de prestación para prestación: {}", prestacionId);

        return indicacionPrestacionRepository.findAllByPrestacionIdAndDeletedAtIsNull(prestacionId);

    }

    /**
     * Realiza la baja lógica de una indicación de prestación.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} indicación a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteIndicacionPrestacion(IndicacionPrestacion indicacionPrestacion, String motivo) {

        log.debug("Dando de baja indicación de prestación: id={}, motivo={}", indicacionPrestacion.getId(), motivo);

        indicacionPrestacion.setDeletedAt(Instant.now());
        indicacionPrestacion.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveIndicacionPrestacion(indicacionPrestacion);

    }

    /**
     * Realiza la baja lógica de todas las indicaciones activas de una prestación.
     * Utilizada cuando se da de baja la prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteIndicacionesPrestacionByPrestacion(UUID prestacionId, String motivo) {

        log.debug("Dando de baja todas las indicaciones de la prestación: {}", prestacionId);

        List<IndicacionPrestacion> indicacionesActivas = findIndicacionesPrestacionByPrestacionId(prestacionId);

        for (IndicacionPrestacion indicacion : indicacionesActivas) {
            softDeleteIndicacionPrestacion(indicacion, motivo);
        }

    }

    //endregion

}
