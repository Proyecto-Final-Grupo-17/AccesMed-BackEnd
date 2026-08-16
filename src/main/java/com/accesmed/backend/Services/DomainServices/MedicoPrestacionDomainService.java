package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.MedicoPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code MedicoPrestacion}.
 * Encapsula guardar, buscar, validaciones de reglas de negocio y el cierre de vigencia de
 * la asignación de una prestación a un médico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoPrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoPrestacionRepository medicoPrestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una asignación médico-prestación en la base de datos.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} entidad a persistir
     * @return {@code MedicoPrestacion} la asignación guardada
     */
    public MedicoPrestacion saveMedicoPrestacion(MedicoPrestacion medicoPrestacion) {

        log.debug("Guardando asignación médico-prestación: médico={}, prestación={}",
                medicoPrestacion.getMedico().getId(), medicoPrestacion.getPrestacion().getId());

        return medicoPrestacionRepository.save(medicoPrestacion);

    }

    /**
     * Busca una asignación médico-prestación vigente en una fecha dada, por su
     * identificador.
     *
     * @param id {@code UUID} identificador de la asignación
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code MedicoPrestacion} la asignación vigente correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una asignación vigente con ese id en esa fecha
     */
    public MedicoPrestacion findMedicoPrestacionVigenteById(UUID id, ZonedDateTime fecha) {

        log.debug("Buscando asignación médico-prestación vigente por id: {}, fecha: {}", id, fecha);

        return medicoPrestacionRepository.findByIdAndVigenteAt(id, fecha)
                .orElseThrow(() -> {
                    log.warn("No se encontró la asignación médico-prestación vigente: id={}, fecha={}", id, fecha);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_PRESTACION_NO_ENCONTRADA",
                            "No existe una asignación médico-prestación vigente con el id " + id);
                });

    }

    /**
     * Busca las asignaciones vigentes en una fecha dada de un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code List<MedicoPrestacion>} las asignaciones vigentes de ese médico
     */
    public List<MedicoPrestacion> findAsignacionesVigentesByMedico(UUID medicoId, ZonedDateTime fecha) {

        log.debug("Buscando asignaciones vigentes del médico: {}, fecha: {}", medicoId, fecha);

        return medicoPrestacionRepository.findByMedico_IdAndVigenteAt(medicoId, fecha);

    }

    /**
     * Valida que el período {@code [desde, hasta)} indicado no se solape con ninguna
     * vigencia ya existente entre el médico y la prestación indicados.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param desde {@code ZonedDateTime} inicio del período a validar
     * @param hasta {@code ZonedDateTime} fin del período a validar, o {@code null} si es abierto
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el período se solapa con
     *         una vigencia existente entre ambos
     */
    public void validateSinSolapamiento(UUID medicoId, UUID prestacionId, ZonedDateTime desde, ZonedDateTime hasta) {

        if (medicoPrestacionRepository.existsSolapamiento(medicoId, prestacionId, desde, hasta)) {
            log.warn("No se pudo asignar la prestación {} al médico {}: el período [{}, {}) se solapa con una vigencia existente",
                    prestacionId, medicoId, desde, hasta);
            throw new ReglaNegocioException(getClass(), "MEDICO_PRESTACION_SOLAPADA",
                    "El período indicado se solapa con una asignación vigente entre el médico " + medicoId
                            + " y la prestación " + prestacionId + ".");
        }

    }

    /**
     * Valida que la especialidad de la prestación coincida con la del médico. Regla no
     * expresable en el esquema, anotada en el Javadoc de {@link MedicoPrestacion}.
     *
     * @param medico {@code Medico} médico a validar
     * @param prestacion {@code Prestacion} prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si las especialidades no coinciden
     */
    public void validateEspecialidadCoincide(Medico medico, Prestacion prestacion) {

        if (!medico.getEspecialidad().getId().equals(prestacion.getEspecialidad().getId())) {
            log.warn("No se pudo asignar la prestación {} al médico {}: especialidad de la prestación ({}) distinta a la del médico ({})",
                    prestacion.getId(), medico.getId(), prestacion.getEspecialidad().getId(), medico.getEspecialidad().getId());
            throw new ReglaNegocioException(getClass(), "MEDICO_PRESTACION_ESPECIALIDAD_DISTINTA",
                    "La especialidad de la prestación " + prestacion.getId()
                            + " no coincide con la especialidad del médico " + medico.getId() + ".");
        }

    }

    /**
     * Cierra la vigencia de una asignación médico-prestación, reemplazando su
     * {@code fechaFinVigencia}. Admite una fecha futura para programar el corte. El piso
     * duro contra turnos vivos se valida antes, en el {@code App} (consulta a
     * {@code TurnoDomainService}), no acá.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} asignación a cerrar
     * @param fechaFinVigencia {@code ZonedDateTime} fecha en la que deja de estar vigente
     * @throws ValidacionException {@code ValidacionException} si {@code fechaFinVigencia} no es
     *         posterior a {@code fechaInicioVigencia}
     */
    public void cerrarVigenciaMedicoPrestacion(MedicoPrestacion medicoPrestacion, ZonedDateTime fechaFinVigencia) {

        if (!fechaFinVigencia.isAfter(medicoPrestacion.getFechaInicioVigencia())) {
            log.warn("No se pudo cerrar la vigencia de la asignación médico-prestación: fechaFinVigencia {} no es posterior a fechaInicioVigencia {}",
                    fechaFinVigencia, medicoPrestacion.getFechaInicioVigencia());
            throw new ValidacionException(getClass(),
                    List.of("La fecha de fin de vigencia debe ser posterior a la fecha de inicio de vigencia."));
        }

        log.debug("Cerrando vigencia de asignación médico-prestación: id={}, fechaFinVigencia={}",
                medicoPrestacion.getId(), fechaFinVigencia);

        medicoPrestacion.setFechaFinVigencia(fechaFinVigencia);

        saveMedicoPrestacion(medicoPrestacion);

    }

    /**
     * Verifica si existe una asignación médico-prestación vigente en una fecha dada,
     * entre el médico y la prestación indicados. Regla de Agenda (§5 AGEN, habilitada por
     * A1): cada slot exige una {@code MedicoPrestacion} vigente en la fecha del slot, no
     * en "ahora".
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code boolean} {@code true} si existe una asignación vigente entre ambos en ese instante
     */
    public boolean existsVigenteEnFecha(UUID medicoId, UUID prestacionId, ZonedDateTime fecha) {

        log.debug("Verificando vigencia médico-prestación: médico={}, prestación={}, fecha={}", medicoId, prestacionId, fecha);

        return medicoPrestacionRepository.existsVigenteEnFecha(medicoId, prestacionId, fecha);

    }

    /**
     * Cierra la vigencia de todas las asignaciones médico-prestación vigentes de una
     * prestación. Utilizada cuando se deshabilita la prestación (A4, paso 1).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaFinVigencia {@code ZonedDateTime} fecha en la que dejan de estar vigentes
     */
    public void cerrarVigenciasByPrestacion(UUID prestacionId, ZonedDateTime fechaFinVigencia) {

        log.debug("Cerrando vigencia de todas las asignaciones médico-prestación de la prestación: {}", prestacionId);

        List<MedicoPrestacion> asignacionesVigentes = medicoPrestacionRepository.findByPrestacion_IdAndVigenteAt(prestacionId, fechaFinVigencia);

        for (MedicoPrestacion medicoPrestacion : asignacionesVigentes) {
            cerrarVigenciaMedicoPrestacion(medicoPrestacion, fechaFinVigencia);
        }

    }

    //endregion

}
