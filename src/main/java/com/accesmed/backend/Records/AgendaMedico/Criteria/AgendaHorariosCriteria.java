package com.accesmed.backend.Records.AgendaMedico.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.BooleanFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.LocalDateFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.LocalTimeFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code AgendaHorarios} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Lo comparten {@code listHorariosAgenda}
 * y {@code listHorariosDisponibles}: se diferencian solo en las guardas fijas que aplica
 * cada uno en el {@code QueryService}, no en los filtros disponibles.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class AgendaHorariosCriteria {

    private UUIDFilter id;
    private UUIDFilter agendaMedicoId;
    private UUIDFilter medicoId;
    private UUIDFilter prestacionId;
    private LocalDateFilter fecha;
    private LocalTimeFilter horaDesde;
    private BooleanFilter estaOcupada;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code AgendaHorariosDiaQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

}
