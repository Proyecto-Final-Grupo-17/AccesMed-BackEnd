package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code IndicacionPrestacion}.
 * Las consultas de vigencia evalúan {@code fechaInicioVigencia}/{@code fechaFinVigencia}
 * contra una fecha recibida por parámetro, ya que la combinación AND/OR no se puede
 * expresar por derivación de nombre de Spring Data. Extiende {@code JpaSpecificationExecutor}
 * para el filtrado dinámico de {@code IndicacionPrestacionQueryService}
 * (ver {@code Docs/ARQUITECTURA.md §7}).
 */
@Repository
public interface IndicacionPrestacionRepository extends JpaRepository<IndicacionPrestacion, UUID>, JpaSpecificationExecutor<IndicacionPrestacion> {

    /**
     * Busca una indicación de prestación vigente en una fecha dada, por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @param hoy {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code Optional<IndicacionPrestacion>} la indicación si existe y está vigente,
     *         {@code Optional.empty()} en caso contrario
     */
    @Query("SELECT i FROM IndicacionPrestacion i WHERE i.id = :id "
            + "AND i.fechaInicioVigencia <= :hoy AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia >= :hoy)")
    Optional<IndicacionPrestacion> findVigenteById(@Param("id") UUID id, @Param("hoy") LocalDate hoy);

    /**
     * Lista las indicaciones de prestación vigentes en una fecha dada, asociadas a una
     * prestación determinada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param hoy {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes de esa prestación
     */
    @Query("SELECT i FROM IndicacionPrestacion i WHERE i.prestacion.id = :prestacionId "
            + "AND i.fechaInicioVigencia <= :hoy AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia >= :hoy)")
    List<IndicacionPrestacion> findAllVigentesByPrestacionId(@Param("prestacionId") UUID prestacionId, @Param("hoy") LocalDate hoy);

    /**
     * Lista todas las indicaciones de prestación vigentes en una fecha dada.
     *
     * @param hoy {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes
     */
    @Query("SELECT i FROM IndicacionPrestacion i "
            + "WHERE i.fechaInicioVigencia <= :hoy AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia >= :hoy)")
    List<IndicacionPrestacion> findAllVigentes(@Param("hoy") LocalDate hoy);

    /**
     * Verifica si existe alguna indicación de prestación vigente en una fecha dada que
     * referencie un tipo de indicación determinado.
     *
     * @param tipoIndicacionPrestacionId {@code UUID} identificador del tipo de indicación
     * @param hoy {@code LocalDate} fecha contra la cual evaluar la vigencia
     * @return {@code boolean} {@code true} si existe una indicación vigente que lo referencia,
     *         {@code false} en caso contrario
     */
    @Query("SELECT COUNT(i) > 0 FROM IndicacionPrestacion i WHERE i.tipoIndicacionPrestacion.id = :tipoIndicacionPrestacionId "
            + "AND i.fechaInicioVigencia <= :hoy AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia >= :hoy)")
    boolean existsVigenteByTipoIndicacionPrestacionId(@Param("tipoIndicacionPrestacionId") UUID tipoIndicacionPrestacionId, @Param("hoy") LocalDate hoy);

}
