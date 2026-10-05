package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code IndicacionPrestacionTurno}.
 * Todas las consultas excluyen las indicaciones dadas de baja ({@code deletedAt IS NULL}),
 * salvo que el nombre del método indique lo contrario.
 */
@Repository
public interface IndicacionPrestacionTurnoRepository
        extends JpaRepository<IndicacionPrestacionTurno, UUID>, JpaSpecificationExecutor<IndicacionPrestacionTurno> {

    /**
     * Busca todas las indicaciones activas de un turno.
     *
     * @param turnoId {@code UUID} identificador del turno
     * @return {@code List<IndicacionPrestacionTurno>} las indicaciones activas de ese turno
     */
    List<IndicacionPrestacionTurno> findByTurno_IdAndDeletedAtIsNull(UUID turnoId);

}
