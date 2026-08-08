package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.MedicoPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code MedicoPrestacion}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface MedicoPrestacionRepository extends JpaRepository<MedicoPrestacion, UUID> {

    /**
     * Busca una asignación médico-prestación activa por su identificador.
     *
     * @param id {@code UUID} identificador de la asignación
     * @return {@code Optional<MedicoPrestacion>} la asignación si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<MedicoPrestacion> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe una asignación activa entre el médico y la prestación indicados.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code boolean} {@code true} si existe una asignación activa entre ambos
     */
    boolean existsByMedico_IdAndPrestacion_IdAndDeletedAtIsNull(UUID medicoId, UUID prestacionId);

    /**
     * Busca las asignaciones activas de un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @return {@code List<MedicoPrestacion>} las asignaciones activas de ese médico
     */
    List<MedicoPrestacion> findByMedico_IdAndDeletedAtIsNull(UUID medicoId);

}
