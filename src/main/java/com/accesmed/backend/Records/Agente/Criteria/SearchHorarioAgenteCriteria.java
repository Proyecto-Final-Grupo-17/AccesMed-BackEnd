package com.accesmed.backend.Records.Agente.Criteria;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Criterios, ya resueltos a identificadores, con los que {@code AgendaHorariosDiaQueryService} busca
 * horarios para el canal chatbot. Un campo {@code null} no filtra; un conjunto vacío no devuelve nada.
 *
 * @param medicoIds {@code Set<UUID>} médicos candidatos, o {@code null}
 * @param prestacionIds {@code Set<UUID>} prestaciones candidatas, o {@code null}
 * @param especialidadIds {@code Set<UUID>} especialidades de la prestación, o {@code null}
 * @param fechaDesde {@code LocalDate} primer día, inclusive, o {@code null}
 * @param fechaHasta {@code LocalDate} último día, inclusive, o {@code null}
 * @param fechas {@code Collection<LocalDate>} días permitidos (por ejemplo, todos los jueves del período), o {@code null}
 * @param horaDesdeMinima {@code LocalTime} hora de inicio mínima, inclusive, o {@code null}
 * @param horaDesdeMaxima {@code LocalTime} hora de inicio máxima, exclusive, o {@code null}
 */
public record SearchHorarioAgenteCriteria(
        Set<UUID> medicoIds,
        Set<UUID> prestacionIds,
        Set<UUID> especialidadIds,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Collection<LocalDate> fechas,
        LocalTime horaDesdeMinima,
        LocalTime horaDesdeMaxima
) {

    /**
     * Copia los criterios restringidos a unos días concretos.
     *
     * @param fechasPermitidas {@code Collection<LocalDate>} días a los que se limita la búsqueda
     * @return {@code SearchHorarioAgenteCriteria} los mismos criterios con {@code fechas} reemplazado
     */
    public SearchHorarioAgenteCriteria withFechas(Collection<LocalDate> fechasPermitidas) {

        SearchHorarioAgenteCriteria criteriaConFechas = new SearchHorarioAgenteCriteria(medicoIds, prestacionIds,
                especialidadIds, fechaDesde, fechaHasta, fechasPermitidas, horaDesdeMinima, horaDesdeMaxima);
        return criteriaConFechas;

    }

}
