package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.AgendaMedico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code AgendaMedico}. El no solapamiento
 * de períodos del mismo médico lo refuerza además el esquema con una constraint
 * {@code EXCLUDE USING gist} (ver changelog); esta consulta da el mismo enforcement con un
 * mensaje de error legible antes de llegar a la base.
 */
@Repository
public interface AgendaMedicoRepository extends JpaRepository<AgendaMedico, UUID>,
        JpaSpecificationExecutor<AgendaMedico> {

    /**
     * Verifica si el período {@code [desde, hasta]} indicado (ambos bordes inclusive) se
     * solapa con algún período de vigencia ya existente del médico. Excluye la propia
     * agenda cuando se valida un update ({@code excludeId} distinto de {@code null}).
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param desde {@code LocalDate} inicio del período a validar
     * @param hasta {@code LocalDate} fin del período a validar
     * @param excludeId {@code UUID} identificador de agenda a excluir de la comparación, o {@code null}
     * @return {@code boolean} {@code true} si el período se solapa con un período existente del médico
     */
    @Query("SELECT COUNT(a) > 0 FROM AgendaMedico a WHERE a.medico.id = :medicoId "
            + "AND (:excludeId IS NULL OR a.id <> :excludeId) "
            + "AND a.fechaInicioVigencia <= :hasta AND a.fechaFinVigencia >= :desde")
    boolean existsSolapamiento(@Param("medicoId") UUID medicoId, @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta, @Param("excludeId") UUID excludeId);

}
