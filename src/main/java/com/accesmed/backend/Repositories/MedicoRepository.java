package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repositorio de solo lectura para la entidad {@code Medico}. No construye el módulo
 * Médico (sin stack de escritura): existe para el enforcement real de la precondición
 * restrictiva de baja de Especialidad contra médicos activos.
 */
@Repository
public interface MedicoRepository extends JpaRepository<Medico, UUID> {

    /**
     * Verifica si existe un médico activo con la especialidad indicada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code boolean} {@code true} si existe al menos un médico activo con esa especialidad
     */
    boolean existsByEspecialidadIdAndDeletedAtIsNull(UUID especialidadId);

}
