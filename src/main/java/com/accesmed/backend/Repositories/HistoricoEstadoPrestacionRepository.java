package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.EstadoPrestacion;
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

    /**
     * Busca el tramo vigente (sin {@code fechaHoraFin}) de una prestación, excluyendo un
     * estado puntual. Útil para encontrar el tramo activo que no sea {@code DESHABILITADA}
     * (transición terminal, sin tramo posterior).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param estado {@code EstadoPrestacion} estado a excluir de la búsqueda
     * @return {@code Optional<HistoricoEstadoPrestacion>} el tramo vigente que no está en
     *         ese estado, si existe
     */
    Optional<HistoricoEstadoPrestacion> findByPrestacionIdAndFechaHoraFinIsNullAndEstadoNot(UUID prestacionId, EstadoPrestacion estado);

}
