package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@code Especialidad}.
 * Todas las consultas filtran registros con baja lógica ({@code deletedAt IS NULL}).
 * Extiende {@code JpaSpecificationExecutor} para el filtrado dinámico de
 * {@code EspecialidadQueryService} (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface EspecialidadRepository extends JpaRepository<Especialidad, UUID>, JpaSpecificationExecutor<Especialidad> {

    /**
     * Busca una especialidad activa por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code Optional<Especialidad>} la especialidad si existe y está activa,
     *         {@code Optional.empty()} en caso contrario
     */
    Optional<Especialidad> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Verifica si existe una especialidad activa con el código especificado.
     *
     * @param codigo {@code String} código a verificar
     * @return {@code boolean} {@code true} si existe una especialidad activa con ese código
     */
    boolean existsByCodigoIgnoreCaseAndDeletedAtIsNull(String codigo);

    /**
     * Verifica si existe una especialidad activa con el código especificado, excluyendo
     * un id concreto (útil para validar unicidad al actualizar).
     *
     * @param codigo {@code String} código a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra especialidad activa con ese código
     */
    boolean existsByCodigoIgnoreCaseAndDeletedAtIsNullAndIdNot(String codigo, UUID id);

    /**
     * Verifica si existe una especialidad activa con el nombre especificado.
     *
     * @param nombre {@code String} nombre a verificar
     * @return {@code boolean} {@code true} si existe una especialidad activa con ese nombre
     */
    boolean existsByNombreIgnoreCaseAndDeletedAtIsNull(String nombre);

    /**
     * Verifica si existe una especialidad activa con el nombre especificado, excluyendo
     * un id concreto (útil para validar unicidad al actualizar).
     *
     * @param nombre {@code String} nombre a verificar
     * @param id {@code UUID} id a excluir de la búsqueda
     * @return {@code boolean} {@code true} si existe otra especialidad activa con ese nombre
     */
    boolean existsByNombreIgnoreCaseAndDeletedAtIsNullAndIdNot(String nombre, UUID id);

    /**
     * Busca una especialidad activa por su nombre, sin distinguir mayúsculas.
     *
     * @param nombre {@code String} nombre de la especialidad
     * @return {@code Optional<Especialidad>} la especialidad activa, o vacío si no existe
     */
    Optional<Especialidad> findByNombreIgnoreCaseAndDeletedAtIsNull(String nombre);

    /**
     * Lista las especialidades activas ordenadas por nombre.
     *
     * @return {@code List<Especialidad>} las especialidades activas
     */
    List<Especialidad> findByDeletedAtIsNullOrderByNombreAsc();

    /**
     * Busca especialidades activas por similitud de trigramas con el nombre (canal chatbot,
     * fallback de la búsqueda por palabras): tolera errores de tipeo. Usa {@code word_similarity}
     * de {@code pg_trgm} sobre {@code lower(unaccent(nombre))}.
     *
     * @param consulta {@code String} consulta ya normalizada (minúsculas, sin tildes)
     * @param umbral {@code double} similitud mínima (0 a 1)
     * @param limite {@code int} cantidad máxima de resultados
     * @return {@code List<Especialidad>} las especialidades más parecidas primero
     */
    @Query(value = "SELECT e.* FROM especialidad e WHERE e.deleted_at IS NULL "
            + "AND word_similarity(:consulta, lower(unaccent(e.nombre))) >= :umbral "
            + "ORDER BY word_similarity(:consulta, lower(unaccent(e.nombre))) DESC, e.nombre LIMIT :limite",
            nativeQuery = true)
    List<Especialidad> findActivasBySimilitudNombre(@Param("consulta") String consulta, @Param("umbral") double umbral,
            @Param("limite") int limite);

}
