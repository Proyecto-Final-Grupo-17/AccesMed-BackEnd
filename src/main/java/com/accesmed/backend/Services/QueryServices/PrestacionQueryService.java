package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.EstadoPrestacion;
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
     * Lista todas las prestaciones.
     *
     * @return {@code List<Prestacion>} lista de todas las prestaciones
     */
    public List<Prestacion> findAllPrestaciones() {

        log.debug("Listando todas las prestaciones");

        return prestacionRepository.findAll();

    }

    /**
     * Lista todas las prestaciones de una especialidad determinada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Prestacion>} lista de prestaciones de esa especialidad
     */
    public List<Prestacion> findPrestacionesByEspecialidad(UUID especialidadId) {

        log.debug("Listando prestaciones de especialidad: {}", especialidadId);

        return prestacionRepository.findAllByEspecialidadId(especialidadId);

    }

    /**
     * Lista todas las prestaciones en un estado determinado.
     *
     * @param estadoActual {@code EstadoPrestacion} estado a filtrar
     * @return {@code List<Prestacion>} lista de prestaciones en ese estado
     */
    public List<Prestacion> findPrestacionesByEstadoActual(EstadoPrestacion estadoActual) {

        log.debug("Listando prestaciones en estado: {}", estadoActual);

        return prestacionRepository.findAllByEstadoActual(estadoActual);

    }

    /**
     * Lista todas las prestaciones de una especialidad determinada en un estado dado.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @param estadoActual {@code EstadoPrestacion} estado a filtrar
     * @return {@code List<Prestacion>} lista de prestaciones de esa especialidad en ese estado
     */
    public List<Prestacion> findPrestacionesByEspecialidadAndEstadoActual(UUID especialidadId, EstadoPrestacion estadoActual) {

        log.debug("Listando prestaciones de especialidad {} en estado: {}", especialidadId, estadoActual);

        return prestacionRepository.findAllByEspecialidadIdAndEstadoActual(especialidadId, estadoActual);

    }

    //endregion

}
