package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Repositories.IndicacionPrestacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
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
     * Lista todas las indicaciones de prestación vigentes.
     *
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes
     */
    public List<IndicacionPrestacion> findAllIndicacionesPrestacion() {

        log.debug("Listando todas las indicaciones de prestación vigentes");

        return indicacionPrestacionRepository.findAllVigentes(ZonedDateTime.now());

    }

    /**
     * Lista todas las indicaciones de prestación vigentes asociadas a una prestación determinada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes de esa prestación
     */
    public List<IndicacionPrestacion> findIndicacionesPrestacionByPrestacion(UUID prestacionId) {

        log.debug("Listando indicaciones de prestación para prestación: {}", prestacionId);

        return indicacionPrestacionRepository.findAllVigentesByPrestacionId(prestacionId, ZonedDateTime.now());

    }

    //endregion

}
