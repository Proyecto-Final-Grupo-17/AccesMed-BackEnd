package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Repositories.IndicacionPrestacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code IndicacionPrestacion}.
 * Solo contiene métodos de búsqueda y listado, sin lógica de modificación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicacionPrestacionQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionRepository indicacionPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Lista todas las indicaciones de prestación activas.
     *
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones activas
     */
    public List<IndicacionPrestacion> findAllIndicacionesPrestacion() {

        log.debug("Listando todas las indicaciones de prestación activas");

        return indicacionPrestacionRepository.findAllByDeletedAtIsNull();

    }

    /**
     * Lista todas las indicaciones de prestación activas asociadas a una prestación determinada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones activas de esa prestación
     */
    public List<IndicacionPrestacion> findIndicacionesPrestacionByPrestacion(UUID prestacionId) {

        log.debug("Listando indicaciones de prestación para prestación: {}", prestacionId);

        return indicacionPrestacionRepository.findAllByPrestacionIdAndDeletedAtIsNull(prestacionId);

    }

    //endregion

}
