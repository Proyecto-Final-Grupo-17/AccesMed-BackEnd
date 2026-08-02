package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.TipoIndicacionPrestacion;
import com.accesmed.backend.Repositories.TipoIndicacionPrestacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consultas de lectura para la entidad {@code TipoIndicacionPrestacion}.
 * Solo contiene métodos de búsqueda y listado, sin lógica de modificación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TipoIndicacionPrestacionQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionRepository tipoIndicacionPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Lista todos los tipos de indicación de prestación activos.
     *
     * @return {@code List<TipoIndicacionPrestacion>} lista de tipos activos
     */
    public List<TipoIndicacionPrestacion> findAllTiposIndicacionPrestacion() {

        log.debug("Listando todos los tipos de indicación de prestación activos");

        return tipoIndicacionPrestacionRepository.findAllByDeletedAtIsNull();

    }

    //endregion

}
