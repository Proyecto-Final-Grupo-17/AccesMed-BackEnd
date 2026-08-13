package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Repositories.IndicacionPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code IndicacionPrestacion}.
 * Encapsula las operaciones de guardar, buscar y cierre de vigencia.
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
     * Guarda varias indicaciones de prestación en la base de datos en una sola operación.
     *
     * @param indicacionesPrestacion {@code List<IndicacionPrestacion>} entidades a persistir
     * @return {@code List<IndicacionPrestacion>} las indicaciones guardadas
     */
    public List<IndicacionPrestacion> saveIndicacionesPrestacion(List<IndicacionPrestacion> indicacionesPrestacion) {

        log.debug("Guardando {} indicaciones de prestación", indicacionesPrestacion.size());

        return indicacionPrestacionRepository.saveAll(indicacionesPrestacion);

    }

    /**
     * Busca una indicación de prestación vigente por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code IndicacionPrestacion} la indicación vigente correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         indicación vigente con ese id
     */
    public IndicacionPrestacion findIndicacionPrestacionVigenteById(UUID id) {

        log.debug("Buscando indicación de prestación vigente por id: {}", id);

        return indicacionPrestacionRepository.findVigenteById(id, ZonedDateTime.now())
                .orElseThrow(() -> {
                    log.warn("No se encontró la indicación de prestación: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "INDICACION_PRESTACION_NO_ENCONTRADA",
                            "No existe una indicación de prestación vigente con el id " + id);
                });

    }

    /**
     * Verifica si existen indicaciones de prestación vigentes que referencian un tipo de
     * indicación. Usada por la baja restrictiva de {@code TipoIndicacionPrestacion}.
     *
     * @param tipoIndicacionPrestacionId {@code UUID} identificador del tipo de indicación
     * @return {@code boolean} {@code true} si existe al menos una indicación vigente de ese tipo
     */
    public boolean existsIndicacionesVigentesByTipo(UUID tipoIndicacionPrestacionId) {

        return indicacionPrestacionRepository.existsVigenteByTipoIndicacionPrestacionId(tipoIndicacionPrestacionId, ZonedDateTime.now());

    }

    /**
     * Busca todas las indicaciones de prestación vigentes asociadas a una prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes de esa prestación
     */
    public List<IndicacionPrestacion> findIndicacionesPrestacionVigentesByPrestacionId(UUID prestacionId) {

        log.debug("Buscando indicaciones de prestación vigentes para prestación: {}", prestacionId);

        return indicacionPrestacionRepository.findAllVigentesByPrestacionId(prestacionId, ZonedDateTime.now());

    }

    /**
     * Cierra la vigencia de una indicación de prestación, reemplazando su
     * {@code fechaFinVigencia}. Admite una fecha futura para programar el retiro.
     *
     * @param indicacionPrestacion {@code IndicacionPrestacion} indicación a retirar
     * @param fechaFinVigencia {@code ZonedDateTime} fecha en la que deja de estar vigente
     * @throws ValidacionException {@code ValidacionException} si {@code fechaFinVigencia} no es
     *         posterior a {@code fechaInicioVigencia}
     */
    public void cerrarVigenciaIndicacionPrestacion(IndicacionPrestacion indicacionPrestacion, ZonedDateTime fechaFinVigencia) {

        if (!fechaFinVigencia.isAfter(indicacionPrestacion.getFechaInicioVigencia())) {
            log.warn("No se pudo cerrar la vigencia de la indicación: fechaFinVigencia {} no es posterior a fechaInicioVigencia {}",
                    fechaFinVigencia, indicacionPrestacion.getFechaInicioVigencia());
            throw new ValidacionException(getClass(),
                    List.of("La fecha de fin de vigencia debe ser posterior a la fecha de inicio de vigencia."));
        }

        log.debug("Cerrando vigencia de indicación de prestación: id={}, fechaFinVigencia={}",
                indicacionPrestacion.getId(), fechaFinVigencia);

        indicacionPrestacion.setFechaFinVigencia(fechaFinVigencia);

        saveIndicacionPrestacion(indicacionPrestacion);

    }

    /**
     * Cierra la vigencia de todas las indicaciones vigentes de una prestación. Utilizada
     * cuando se deshabilita la prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaFinVigencia {@code ZonedDateTime} fecha en la que dejan de estar vigentes
     */
    public void cerrarVigenciaIndicacionesPrestacionByPrestacion(UUID prestacionId, ZonedDateTime fechaFinVigencia) {

        log.debug("Cerrando vigencia de todas las indicaciones de la prestación: {}", prestacionId);

        List<IndicacionPrestacion> indicacionesVigentes = findIndicacionesPrestacionVigentesByPrestacionId(prestacionId);

        for (IndicacionPrestacion indicacion : indicacionesVigentes) {
            cerrarVigenciaIndicacionPrestacion(indicacion, fechaFinVigencia);
        }

    }

    //endregion

}
