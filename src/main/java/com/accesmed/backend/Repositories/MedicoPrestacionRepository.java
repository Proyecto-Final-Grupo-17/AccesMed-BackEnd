package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.MedicoPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code MedicoPrestacion}.
 * Las consultas de vigencia evalúan {@code fechaInicioVigencia}/{@code fechaFinVigencia}
 * contra un instante recibido por parámetro, ya que la combinación AND/OR no se puede
 * expresar por derivación de nombre de Spring Data.
 */
@Repository
public interface MedicoPrestacionRepository extends JpaRepository<MedicoPrestacion, UUID> {

    /**
     * Busca una asignación médico-prestación vigente en un instante dado, por su
     * identificador.
     *
     * @param id {@code UUID} identificador de la asignación
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code Optional<MedicoPrestacion>} la asignación si existe y está vigente,
     *         {@code Optional.empty()} en caso contrario
     */
    @Query("SELECT mp FROM MedicoPrestacion mp WHERE mp.id = :id "
            + "AND mp.fechaInicioVigencia <= :fecha AND (mp.fechaFinVigencia IS NULL OR :fecha < mp.fechaFinVigencia)")
    Optional<MedicoPrestacion> findByIdAndVigenteAt(@Param("id") UUID id, @Param("fecha") ZonedDateTime fecha);

    /**
     * Lista las asignaciones vigentes en un instante dado de un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code List<MedicoPrestacion>} las asignaciones vigentes de ese médico
     */
    @Query("SELECT mp FROM MedicoPrestacion mp WHERE mp.medico.id = :medicoId "
            + "AND mp.fechaInicioVigencia <= :fecha AND (mp.fechaFinVigencia IS NULL OR :fecha < mp.fechaFinVigencia)")
    List<MedicoPrestacion> findByMedico_IdAndVigenteAt(@Param("medicoId") UUID medicoId, @Param("fecha") ZonedDateTime fecha);

    /**
     * Lista las asignaciones vigentes en un instante dado de una prestación. Utilizada
     * por la cascada de deshabilitación de {@code Prestacion} (A4).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code List<MedicoPrestacion>} las asignaciones vigentes de esa prestación
     */
    @Query("SELECT mp FROM MedicoPrestacion mp WHERE mp.prestacion.id = :prestacionId "
            + "AND mp.fechaInicioVigencia <= :fecha AND (mp.fechaFinVigencia IS NULL OR :fecha < mp.fechaFinVigencia)")
    List<MedicoPrestacion> findByPrestacion_IdAndVigenteAt(@Param("prestacionId") UUID prestacionId, @Param("fecha") ZonedDateTime fecha);

    /**
     * Verifica si existe una asignación vigente en un instante dado entre el médico y la
     * prestación indicados. Usada por el cálculo de slots de Agenda (Fase B) y como
     * precondición de negocio en general.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fecha {@code ZonedDateTime} instante contra el cual evaluar la vigencia
     * @return {@code boolean} {@code true} si existe una asignación vigente entre ambos en ese instante
     */
    @Query("SELECT COUNT(mp) > 0 FROM MedicoPrestacion mp WHERE mp.medico.id = :medicoId AND mp.prestacion.id = :prestacionId "
            + "AND mp.fechaInicioVigencia <= :fecha AND (mp.fechaFinVigencia IS NULL OR :fecha < mp.fechaFinVigencia)")
    boolean existsVigenteEnFecha(@Param("medicoId") UUID medicoId, @Param("prestacionId") UUID prestacionId, @Param("fecha") ZonedDateTime fecha);

    /**
     * Verifica si el período {@code [desde, hasta)} indicado se solapa con alguna
     * vigencia ya existente entre el médico y la prestación indicados. {@code hasta}
     * {@code null} representa un período abierto (sin fecha de corte).
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param desde {@code ZonedDateTime} inicio del período a validar
     * @param hasta {@code ZonedDateTime} fin del período a validar, o {@code null} si es abierto
     * @return {@code boolean} {@code true} si el período se solapa con una vigencia existente
     */
    @Query("SELECT COUNT(mp) > 0 FROM MedicoPrestacion mp WHERE mp.medico.id = :medicoId AND mp.prestacion.id = :prestacionId "
            + "AND (:hasta IS NULL OR mp.fechaInicioVigencia < :hasta) "
            + "AND (mp.fechaFinVigencia IS NULL OR mp.fechaFinVigencia > :desde)")
    boolean existsSolapamiento(@Param("medicoId") UUID medicoId, @Param("prestacionId") UUID prestacionId,
            @Param("desde") ZonedDateTime desde, @Param("hasta") ZonedDateTime hasta);

}
