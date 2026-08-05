package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Repositories.ObraSocialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consultas de lectura para la entidad {@code ObraSocial}.
 * Solo contiene métodos de búsqueda y listado, sin lógica de modificación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObraSocialQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialRepository obraSocialRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Lista todas las obras sociales activas.
     *
     * @return {@code List<ObraSocial>} lista de obras sociales activas
     */
    public List<ObraSocial> findAllObrasSociales() {

        log.debug("Listando todas las obras sociales activas");

        return obraSocialRepository.findAllByDeletedAtIsNull();

    }

    //endregion

}
