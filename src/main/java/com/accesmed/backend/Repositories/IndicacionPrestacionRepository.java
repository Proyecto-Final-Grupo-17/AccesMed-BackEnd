package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code IndicacionPrestacion}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface IndicacionPrestacionRepository extends JpaRepository<IndicacionPrestacion, UUID> {

    /**
     * Busca una indicación de prestación activa por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code Optional<IndicacionPrestacion>} la indicación si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<IndicacionPrestacion> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Lista todas las indicaciones de prestación activas.
     *
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones activas
     */
    List<IndicacionPrestacion> findAllByDeletedAtIsNull();

    /**
     * Lista todas las indicaciones de prestación activas asociadas a una prestación determinada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones activas de esa prestación
     */
    List<IndicacionPrestacion> findAllByPrestacionIdAndDeletedAtIsNull(UUID prestacionId);

    /**
     * Verifica si existe alguna indicación de prestación activa que referencie
     * un tipo de indicación determinado.
     *
     * @param tipoIndicacionPrestacionId {@code UUID} identificador del tipo de indicación
     * @return {@code boolean} {@code true} si existe una indicación activa que lo referencia,
     *         {@code false} en caso contrario
     */
    boolean existsByTipoIndicacionPrestacionIdAndDeletedAtIsNull(UUID tipoIndicacionPrestacionId);

}
