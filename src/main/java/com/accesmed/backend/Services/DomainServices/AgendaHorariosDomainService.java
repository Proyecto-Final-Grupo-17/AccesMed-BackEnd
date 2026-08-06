package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Repositories.AgendaHorariosRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lógica de dominio de solo lectura para la entidad {@code AgendaHorarios}. No
 * construye el módulo de Agenda (sin stack de escritura): existe para el enforcement
 * real de la precondición restrictiva de baja de Prestación contra horarios futuros
 * ocupados, tocando únicamente {@code AgendaHorariosRepository}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaHorariosDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaHorariosRepository agendaHorariosRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Valida que una prestación no tenga horarios de agenda futuros (o de hoy)
     * ocupados. Precondición real de la baja restrictiva de deshabilitar una prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay horarios de
     *         agenda futuros ocupados de esa prestación
     */
    public void validateSinAgendaFuturaOcupada(UUID prestacionId) {

        if (agendaHorariosRepository.existsByPrestacionIdAndEstaOcupadaTrueAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(
                prestacionId, LocalDate.now())) {
            log.warn("No se pudo validar sin agenda futura ocupada para la prestación {}: tiene horarios de agenda futuros ocupados", prestacionId);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CON_AGENDA_OCUPADA",
                    "La prestación " + prestacionId + " tiene horarios de agenda futuros ocupados. No se puede deshabilitar.");
        }

    }

    //endregion

}
