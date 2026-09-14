package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Medico}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 * Extiende {@code JpaSpecificationExecutor} para el filtrado dinámico de
 * {@code MedicoQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface MedicoRepository extends JpaRepository<Medico, UUID>, JpaSpecificationExecutor<Medico> {

    /**
     * Verifica si existe un médico activo con la especialidad indicada.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code boolean} {@code true} si existe al menos un médico activo con esa especialidad
     */
    boolean existsByEspecialidadIdAndDeletedAtIsNull(UUID especialidadId);

    /**
     * Busca un médico activo por su identificador.
     *
     * @param id {@code UUID} identificador del médico
     * @return {@code Optional<Medico>} el médico si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Medico> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe un médico activo con la matrícula especificada.
     *
     * @param matricula {@code String} matrícula a verificar
     * @return {@code boolean} {@code true} si existe un médico activo con esa matrícula
     */
    boolean existsByMatriculaAndDeletedAtIsNull(String matricula);

    /**
     * Verifica si existe un médico activo con la matrícula especificada, excluyendo un id
     * concreto (útil para validar unicidad al actualizar).
     *
     * @param matricula {@code String} matrícula a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro médico activo con esa matrícula
     */
    boolean existsByMatriculaAndDeletedAtIsNullAndIdNot(String matricula, UUID id);

    /**
     * Verifica si existe un médico activo con el DNI especificado.
     *
     * @param dni {@code String} DNI a verificar
     * @return {@code boolean} {@code true} si existe un médico activo con ese DNI
     */
    boolean existsByDniAndDeletedAtIsNull(String dni);

    /**
     * Verifica si existe un médico activo con el DNI especificado, excluyendo un id
     * concreto (útil para validar unicidad al actualizar).
     *
     * @param dni {@code String} DNI a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro médico activo con ese DNI
     */
    boolean existsByDniAndDeletedAtIsNullAndIdNot(String dni, UUID id);

    /**
     * Verifica si existe un médico activo con el email especificado.
     *
     * @param email {@code String} email a verificar
     * @return {@code boolean} {@code true} si existe un médico activo con ese email
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * Verifica si existe un médico activo con el email especificado, excluyendo un id
     * concreto (útil para validar unicidad al actualizar).
     *
     * @param email {@code String} email a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otro médico activo con ese email
     */
    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, UUID id);

}
