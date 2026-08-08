package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Repositories.MedicoPrestacionRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code MedicoPrestacion}.
 * Encapsula guardar, buscar, validaciones de reglas de negocio y la baja lógica de la
 * asignación de una prestación a un médico.
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
     * Busca una asignación médico-prestación activa por su identificador.
     *
     * @param id {@code UUID} identificador de la asignación
     * @return {@code MedicoPrestacion} la asignación activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una asignación activa con ese id
     */
    public MedicoPrestacion findMedicoPrestacionActivaById(UUID id) {

        log.debug("Buscando asignación médico-prestación activa por id: {}", id);

        return medicoPrestacionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la asignación médico-prestación activa: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_PRESTACION_NO_ENCONTRADA",
                            "No existe una asignación médico-prestación activa con el id " + id);
                });

    }

    /**
     * Busca las asignaciones activas de un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @return {@code List<MedicoPrestacion>} las asignaciones activas de ese médico
     */
    public List<MedicoPrestacion> findAsignacionesActivasByMedico(UUID medicoId) {

        log.debug("Buscando asignaciones activas del médico: {}", medicoId);

        return medicoPrestacionRepository.findByMedico_IdAndDeletedAtIsNull(medicoId);

    }

    /**
     * Valida que no exista ya una asignación activa entre el médico y la prestación
     * indicados.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una
     *         asignación activa entre ambos
     */
    public void validateSinAsignacionActiva(UUID medicoId, UUID prestacionId) {

        if (medicoPrestacionRepository.existsByMedico_IdAndPrestacion_IdAndDeletedAtIsNull(medicoId, prestacionId)) {
            log.warn("No se pudo asignar la prestación {} al médico {}: ya existe una asignación activa", prestacionId, medicoId);
            throw new ReglaNegocioException(getClass(), "MEDICO_PRESTACION_YA_ASIGNADA",
                    "El médico " + medicoId + " ya tiene asignada la prestación " + prestacionId + ".");
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
     * Realiza la baja lógica de una asignación médico-prestación.
     *
     * @param medicoPrestacion {@code MedicoPrestacion} asignación a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteMedicoPrestacion(MedicoPrestacion medicoPrestacion, String motivo) {

        log.debug("Dando de baja asignación médico-prestación: id={}, motivo={}", medicoPrestacion.getId(), motivo);

        medicoPrestacion.setDeletedAt(Instant.now());
        medicoPrestacion.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveMedicoPrestacion(medicoPrestacion);

    }

    //endregion

}
