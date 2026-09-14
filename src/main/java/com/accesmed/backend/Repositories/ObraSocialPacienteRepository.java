package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.ObraSocialPaciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code ObraSocialPaciente}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 */
@Repository
public interface ObraSocialPacienteRepository extends JpaRepository<ObraSocialPaciente, UUID> {

    /**
     * Busca una cobertura de obra social activa por su identificador.
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code Optional<ObraSocialPaciente>} la cobertura si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<ObraSocialPaciente> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe una cobertura activa entre el paciente y el plan indicados.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @param planId {@code UUID} identificador del plan
     * @return {@code boolean} {@code true} si existe una cobertura activa entre ambos
     */
    boolean existsByPaciente_IdAndPlan_IdAndDeletedAtIsNull(UUID pacienteId, UUID planId);

    /**
     * Busca las coberturas activas de un paciente.
     *
     * @param pacienteId {@code UUID} identificador del paciente
     * @return {@code List<ObraSocialPaciente>} las coberturas activas de ese paciente
     */
    List<ObraSocialPaciente> findByPaciente_IdAndDeletedAtIsNull(UUID pacienteId);

    /**
     * Busca las coberturas activas que referencian un plan. Usada por la cascada de
     * deshabilitación de {@code Plan} (A5).
     *
     * @param planId {@code UUID} identificador del plan
     * @return {@code List<ObraSocialPaciente>} las coberturas activas de ese plan
     */
    List<ObraSocialPaciente> findByPlan_IdAndDeletedAtIsNull(UUID planId);

}
