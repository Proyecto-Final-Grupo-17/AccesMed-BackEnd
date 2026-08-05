package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.HistoricoEstadoPrestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code HistoricoEstadoPrestacion}.
 */
@Repository
public interface HistoricoEstadoPrestacionRepository extends JpaRepository<HistoricoEstadoPrestacion, UUID> {

    /**
     * Busca el tramo vigente (sin {@code fechaHoraFin}) de una prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code Optional<HistoricoEstadoPrestacion>} el tramo vigente, si existe
     */
    Optional<HistoricoEstadoPrestacion> findByPrestacionIdAndFechaHoraFinIsNull(UUID prestacionId);

}
