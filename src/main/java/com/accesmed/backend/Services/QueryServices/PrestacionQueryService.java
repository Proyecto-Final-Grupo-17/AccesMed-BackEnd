package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.PrestacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Prestacion}.
 * Solo contiene métodos de búsqueda y listado, sin lógica de modificación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionRepository prestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Lista todas las prestaciones activas.
     *
     * @return {@code List<Prestacion>} lista de prestaciones activas
     */
    public List<Prestacion> findAllPrestaciones() {

        log.debug("Listando todas las prestaciones activas");

        return prestacionRepository.findAllByDeletedAtIsNull();

    }

    /**
     * Lista todas las prestaciones activas de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones activas de esa especialidad
     */
    public List<Prestacion> findPrestacionesByEspecialidad(UUID especialidadId) {

        log.debug("Listando prestaciones de especialidad: {}", especialidadId);

        return prestacionRepository.findAllByEspecialidadIdAndDeletedAtIsNull(especialidadId);

    }

    /**
     * Lista todas las prestaciones activas que están habilitadas.
     *
     * @return {@code List<Prestacion>} lista de prestaciones activas habilitadas
     */
    public List<Prestacion> findPrestacionesHabilitadas() {

        log.debug("Listando prestaciones habilitadas");

        return prestacionRepository.findAllByDeletedAtIsNullAndFechaHabilitacionIsNotNull();

    }

    /**
     * Lista todas las prestaciones activas habilitadas de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones activas habilitadas de esa especialidad
     */
    public List<Prestacion> findPrestacionesHabilitadasByEspecialidad(UUID especialidadId) {

        log.debug("Listando prestaciones habilitadas de especialidad: {}", especialidadId);

        return prestacionRepository.findAllByEspecialidadIdAndDeletedAtIsNullAndFechaHabilitacionIsNotNull(especialidadId);

    }

    /**
     * Lista todas las prestaciones activas que están en borrador (no habilitadas).
     *
     * @return {@code List<Prestacion>} lista de prestaciones activas en borrador
     */
    public List<Prestacion> findPrestacionesEnBorrador() {

        log.debug("Listando prestaciones en borrador");

        return prestacionRepository.findAllByDeletedAtIsNullAndFechaHabilitacionIsNull();

    }

    /**
     * Lista todas las prestaciones activas en borrador de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones activas en borrador de esa especialidad
     */
    public List<Prestacion> findPrestacionesEnBorradorByEspecialidad(UUID especialidadId) {

        log.debug("Listando prestaciones en borrador de especialidad: {}", especialidadId);

        return prestacionRepository.findAllByEspecialidadIdAndDeletedAtIsNullAndFechaHabilitacionIsNull(especialidadId);

    }

    //endregion

}
