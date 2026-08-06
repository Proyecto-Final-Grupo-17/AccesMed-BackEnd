package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Repositories.MedicoRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lógica de dominio de solo lectura para la entidad {@code Medico}. No construye el
 * módulo Médico (sin stack de escritura): existe para el enforcement real de la
 * precondición restrictiva de baja de Especialidad contra médicos activos, tocando
 * únicamente {@code MedicoRepository}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoRepository medicoRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Valida que una especialidad no tenga médicos activos asociados. Precondición real
     * de la baja restrictiva de deshabilitar una especialidad.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay médicos activos
     *         de esa especialidad
     */
    public void validateSinMedicosActivos(UUID especialidadId) {

        if (medicoRepository.existsByEspecialidadIdAndDeletedAtIsNull(especialidadId)) {
            log.warn("No se pudo validar sin médicos activos para la especialidad {}: tiene médicos activos", especialidadId);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CON_MEDICOS_ACTIVOS",
                    "La especialidad " + especialidadId + " tiene médicos activos. No se puede dar de baja.");
        }

    }

    //endregion

}
