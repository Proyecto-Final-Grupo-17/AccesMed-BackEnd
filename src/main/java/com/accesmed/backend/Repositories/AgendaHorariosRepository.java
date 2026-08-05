package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.AgendaHorarios;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Repositorio de solo lectura para la entidad {@code AgendaHorarios}. No construye el
 * módulo de Agenda (sin stack de escritura): existe para el enforcement real de la
 * precondición restrictiva de baja de Prestación contra horarios futuros ocupados.
 */
@Repository
public interface AgendaHorariosRepository extends JpaRepository<AgendaHorarios, UUID> {

    /**
     * Verifica si existe un horario ocupado y activo, de fecha futura (o de hoy), de la
     * prestación indicada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @return {@code boolean} {@code true} si existe al menos un horario ocupado futuro de esa prestación
     */
    boolean existsByPrestacionIdAndEstaOcupadaTrueAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(
            UUID prestacionId, LocalDate fechaDesde);

}
