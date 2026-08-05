package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Repositories.EspecialidadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consultas de lectura para la entidad {@code Especialidad}.
 * Solo contiene métodos de búsqueda y listado, sin lógica de modificación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspecialidadQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadRepository especialidadRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Lista todas las especialidades activas.
     *
     * @return {@code List<Especialidad>} lista de especialidades activas
     */
    public List<Especialidad> findAllEspecialidades() {

        log.debug("Listando todas las especialidades activas");

        return especialidadRepository.findAllByDeletedAtIsNull();

    }

    //endregion

}
