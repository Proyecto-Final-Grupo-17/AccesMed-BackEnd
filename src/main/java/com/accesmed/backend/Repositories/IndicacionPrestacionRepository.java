package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.IndicacionPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code IndicacionPrestacion}.
 * Las consultas de vigencia evalúan {@code fechaInicioVigencia}/{@code fechaFinVigencia}
 * contra un instante recibido por parámetro, ya que la combinación AND/OR no se puede
 * expresar por derivación de nombre de Spring Data.
 */
@Repository
public interface IndicacionPrestacionRepository extends JpaRepository<IndicacionPrestacion, UUID> {

    /**
     * Busca una indicación de prestación vigente en un instante dado, por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @param ahora {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code Optional<IndicacionPrestacion>} la indicación si existe y está vigente,
     *         {@code Optional.empty()} en caso contrario
     */
    @Query("SELECT i FROM IndicacionPrestacion i WHERE i.id = :id "
            + "AND i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
    Optional<IndicacionPrestacion> findVigenteById(@Param("id") UUID id, @Param("ahora") ZonedDateTime ahora);

    /**
     * Lista las indicaciones de prestación vigentes en un instante dado, asociadas a una
     * prestación determinada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param ahora {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes de esa prestación
     */
    @Query("SELECT i FROM IndicacionPrestacion i WHERE i.prestacion.id = :prestacionId "
            + "AND i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
    List<IndicacionPrestacion> findAllVigentesByPrestacionId(@Param("prestacionId") UUID prestacionId, @Param("ahora") ZonedDateTime ahora);

    /**
     * Lista todas las indicaciones de prestación vigentes en un instante dado.
     *
     * @param ahora {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code List<IndicacionPrestacion>} lista de indicaciones vigentes
     */
    @Query("SELECT i FROM IndicacionPrestacion i "
            + "WHERE i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
    List<IndicacionPrestacion> findAllVigentes(@Param("ahora") ZonedDateTime ahora);

    /**
     * Verifica si existe alguna indicación de prestación vigente en un instante dado que
     * referencie un tipo de indicación determinado.
     *
     * @param tipoIndicacionPrestacionId {@code UUID} identificador del tipo de indicación
     * @param ahora {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code boolean} {@code true} si existe una indicación vigente que lo referencia,
     *         {@code false} en caso contrario
     */
    @Query("SELECT COUNT(i) > 0 FROM IndicacionPrestacion i WHERE i.tipoIndicacionPrestacion.id = :tipoIndicacionPrestacionId "
            + "AND i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
    boolean existsVigenteByTipoIndicacionPrestacionId(@Param("tipoIndicacionPrestacionId") UUID tipoIndicacionPrestacionId, @Param("ahora") ZonedDateTime ahora);

}
