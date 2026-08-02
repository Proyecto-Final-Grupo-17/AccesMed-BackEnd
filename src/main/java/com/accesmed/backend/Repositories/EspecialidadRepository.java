package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Especialidad}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface EspecialidadRepository extends JpaRepository<Especialidad, UUID> {

    /**
     * Busca una especialidad activa por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code Optional<Especialidad>} la especialidad si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Especialidad> findByIdAndDeletedAtIsNull(UUID id);

}
