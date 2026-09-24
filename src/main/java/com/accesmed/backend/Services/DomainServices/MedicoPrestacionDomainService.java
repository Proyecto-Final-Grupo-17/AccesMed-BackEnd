package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.MedicoPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Utils.FormatoMensaje;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
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
     * @param fecha {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code MedicoPrestacion} la asignación vigente correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una asignación vigente con ese id en esa fecha
     */
    public MedicoPrestacion findMedicoPrestacionVigenteById(UUID id, LocalDate fecha) {

        log.debug("Buscando asignación médico-prestación vigente por id: {}, fecha: {}", id, fecha);

        return medicoPrestacionRepository.findByIdAndVigenteAt(id, fecha)
                .orElseThrow(() -> {
                    log.warn("No se encontró la asignación médico-prestación vigente: id={}, fecha={}", id, fecha);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_PRESTACION_NO_ENCONTRADA",
                            "No se encontró la asignación de prestación solicitada, o ya no está vigente.");
                });

    }

    /**
     * Busca la asignación médico-prestación vigente en una fecha dada, entre el médico y
     * la prestación indicados. A diferencia de {@link #existsVigenteEnFecha}, devuelve la
     * entidad completa (necesaria, por ejemplo, para leer {@code precioParticular}).
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fecha {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code MedicoPrestacion} la asignación vigente entre ambos en esa fecha
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una asignación vigente entre ese médico y esa prestación en esa fecha
     */
    public MedicoPrestacion findVigenteEnFecha(UUID medicoId, UUID prestacionId, LocalDate fecha) {

        log.debug("Buscando asignación médico-prestación vigente: médico={}, prestación={}, fecha={}", medicoId, prestacionId, fecha);

        return medicoPrestacionRepository.findByMedico_IdAndPrestacion_IdAndVigenteAt(medicoId, prestacionId, fecha)
                .orElseThrow(() -> {
                    log.warn("No se encontró asignación médico-prestación vigente: médico={}, prestación={}, fecha={}",
                            medicoId, prestacionId, fecha);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_PRESTACION_NO_ENCONTRADA",
                            "El médico no tiene asignada esa prestación el " + FormatoMensaje.fecha(fecha) + ".");
                });

    }

    /**
     * Busca las asignaciones vigentes en una fecha dada de un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param fecha {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code List<MedicoPrestacion>} las asignaciones vigentes de ese médico
     */
    public List<MedicoPrestacion> findAsignacionesVigentesByMedico(UUID medicoId, LocalDate fecha) {

        log.debug("Buscando asignaciones vigentes del médico: {}, fecha: {}", medicoId, fecha);

        return medicoPrestacionRepository.findByMedico_IdAndVigenteAt(medicoId, fecha);

    }

    /**
     * Valida que el período {@code [desde, hasta]} (ambos extremos inclusivos) indicado no se solape con ninguna
     * vigencia ya existente entre el médico y la prestación indicados.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param desde {@code LocalDate} inicio del período a validar
     * @param hasta {@code LocalDate} fin del período a validar, o {@code null} si es abierto
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el período se solapa con
     *         una vigencia existente entre ambos
     */
    public void validateSinSolapamiento(UUID medicoId, UUID prestacionId, LocalDate desde, LocalDate hasta) {

        if (medicoPrestacionRepository.existsSolapamiento(medicoId, prestacionId, desde, hasta)) {
            log.warn("No se pudo asignar la prestación {} al médico {}: el período [{}, {}] se solapa con una vigencia existente",
                    prestacionId, medicoId, desde, hasta);
            throw new ReglaNegocioException(getClass(), "MEDICO_PRESTACION_SOLAPADA",
                    "El médico ya tiene asignada esa prestación en un período que se superpone con el indicado. Elegí fechas que no se pisen.");
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
                    "No se puede asignar la prestación: su especialidad no coincide con la del médico.");
        }

    }

    /**
     * Cierra la vigencia de una asignación médico-prestación, reemplazando su
     * {@code fechaFinVigencia}. Admite una fecha futura para programar el corte. El piso
     * duro contra turnos vivos se valida antes, en el {@code App} (consulta a
     * {@code TurnoDomainService}), no acá.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} asignación a cerrar
     * @param fechaFinVigencia {@code LocalDate} último día en que está vigente (inclusive)
     * @throws ValidacionException {@code ValidacionException} si {@code fechaFinVigencia} es
     *         anterior a {@code fechaInicioVigencia}
     */
    public void cerrarVigenciaMedicoPrestacion(MedicoPrestacion medicoPrestacion, LocalDate fechaFinVigencia) {

        if (fechaFinVigencia.isBefore(medicoPrestacion.getFechaInicioVigencia())) {
            log.warn("No se pudo cerrar la vigencia de la asignación médico-prestación {}: fechaFinVigencia {} es anterior a fechaInicioVigencia {}",
                    medicoPrestacion.getId(), fechaFinVigencia, medicoPrestacion.getFechaInicioVigencia());
            throw new ValidacionException(getClass(),
                    List.of("La fecha de fin de la asignación no puede ser anterior a su fecha de inicio ("
                            + FormatoMensaje.fecha(medicoPrestacion.getFechaInicioVigencia()) + ")."));
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
     * @param fecha {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code boolean} {@code true} si existe una asignación vigente entre ambos en esa fecha
     */
    public boolean existsVigenteEnFecha(UUID medicoId, UUID prestacionId, LocalDate fecha) {

        log.debug("Verificando vigencia médico-prestación: médico={}, prestación={}, fecha={}", medicoId, prestacionId, fecha);

        return medicoPrestacionRepository.existsVigenteEnFecha(medicoId, prestacionId, fecha);

    }

    /**
     * Lista todas las asignaciones (vigentes o no) de un médico para un lote de
     * prestaciones, en una sola consulta. Pensado para evaluar la vigencia de varios pares
     * (fecha, prestación) en memoria sin incurrir en N+1 (Fase 3 bis #2 de Agenda).
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionIds {@code Collection<UUID>} identificadores de las prestaciones
     * @return {@code List<MedicoPrestacion>} las asignaciones del médico para esas prestaciones
     */
    public List<MedicoPrestacion> findAsignacionesByMedicoAndPrestaciones(UUID medicoId, Collection<UUID> prestacionIds) {

        if (prestacionIds.isEmpty()) {
            return List.of();
        }

        log.debug("Buscando asignaciones del médico {} para {} prestación(es)", medicoId, prestacionIds.size());

        return medicoPrestacionRepository.findByMedico_IdAndPrestacion_IdIn(medicoId, prestacionIds);

    }

    /**
     * Cierra la vigencia de todas las asignaciones médico-prestación vigentes de una
     * prestación. Utilizada cuando se deshabilita la prestación (A4, paso 1).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaFinVigencia {@code LocalDate} último día en que están vigentes (inclusive)
     */
    public void cerrarVigenciasByPrestacion(UUID prestacionId, LocalDate fechaFinVigencia) {

        log.debug("Cerrando vigencia de todas las asignaciones médico-prestación de la prestación: {}", prestacionId);

        List<MedicoPrestacion> asignacionesVigentes = medicoPrestacionRepository.findByPrestacion_IdAndVigenteAt(prestacionId, fechaFinVigencia);

        for (MedicoPrestacion medicoPrestacion : asignacionesVigentes) {
            cerrarVigenciaMedicoPrestacion(medicoPrestacion, fechaFinVigencia);
        }

    }

    //endregion

}
