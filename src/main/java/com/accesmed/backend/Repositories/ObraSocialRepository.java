package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.ObraSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code ObraSocial}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 * Extiende {@code JpaSpecificationExecutor} para el filtrado dinámico de
 * {@code ObraSocialQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface ObraSocialRepository extends JpaRepository<ObraSocial, UUID>, JpaSpecificationExecutor<ObraSocial> {

    /**
     * Busca una obra social activa por su identificador.
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code Optional<ObraSocial>} la obra social si existe y está activa
     */
    Optional<ObraSocial> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe una obra social activa con el código especificado.
     *
     * @param codigo {@code String} código a verificar
     * @return {@code boolean} {@code true} si existe una obra social activa con ese código
     */
    boolean existsByCodigoAndDeletedAtIsNull(String codigo);

    /**
     * Verifica si existe una obra social activa con el código especificado, excluyendo
     * un id concreto.
     *
     * @param codigo {@code String} código a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra obra social activa con ese código
     */
    boolean existsByCodigoAndDeletedAtIsNullAndIdNot(String codigo, UUID id);

    /**
     * Verifica si existe una obra social activa con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe una obra social activa con ese nombre
     */
    boolean existsByNombreAndDeletedAtIsNull(String nombre);

    /**
     * Verifica si existe una obra social activa con el nombre especificado, excluyendo
     * un id concreto.
     *
     * @param nombre {@code String} nombre a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra obra social activa con ese nombre
     */
    boolean existsByNombreAndDeletedAtIsNullAndIdNot(String nombre, UUID id);

}
