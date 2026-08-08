package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Paciente}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 * Extiende {@code JpaSpecificationExecutor} para el filtrado dinámico de
 * {@code PacienteQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface PacienteRepository extends JpaRepository<Paciente, UUID>, JpaSpecificationExecutor<Paciente> {

    /**
     * Busca un paciente activo por su identificador.
     *
     * @param id {@code UUID} identificador del paciente
     * @return {@code Optional<Paciente>} el paciente si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Paciente> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe un paciente activo con el DNI especificado.
     *
     * @param dni {@code String} DNI a verificar
     * @return {@code boolean} {@code true} si existe un paciente activo con ese DNI
     */
    boolean existsByDniAndDeletedAtIsNull(String dni);

    /**
     * Verifica si existe un paciente activo con el DNI especificado, excluyendo un id
     * concreto (útil para validar unicidad al actualizar).
     *
     * @param dni {@code String} DNI a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro paciente activo con ese DNI
     */
    boolean existsByDniAndDeletedAtIsNullAndIdNot(String dni, UUID id);

    /**
     * Verifica si existe un paciente activo con el número de teléfono especificado.
     *
     * @param numeroTelefono {@code String} número de teléfono a verificar
     * @return {@code boolean} {@code true} si existe un paciente activo con ese número de teléfono
     */
    boolean existsByNumeroTelefonoAndDeletedAtIsNull(String numeroTelefono);

    /**
     * Verifica si existe un paciente activo con el número de teléfono especificado,
     * excluyendo un id concreto (útil para validar unicidad al actualizar).
     *
     * @param numeroTelefono {@code String} número de teléfono a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro paciente activo con ese número de teléfono
     */
    boolean existsByNumeroTelefonoAndDeletedAtIsNullAndIdNot(String numeroTelefono, UUID id);

    /**
     * Verifica si existe un paciente activo con el email especificado.
     *
     * @param email {@code String} email a verificar
     * @return {@code boolean} {@code true} si existe un paciente activo con ese email
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * Verifica si existe un paciente activo con el email especificado, excluyendo un id
     * concreto (útil para validar unicidad al actualizar).
     *
     * @param email {@code String} email a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro paciente activo con ese email
     */
    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, UUID id);

}
