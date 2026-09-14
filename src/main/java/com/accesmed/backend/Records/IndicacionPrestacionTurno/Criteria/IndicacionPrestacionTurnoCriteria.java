package com.accesmed.backend.Records.IndicacionPrestacionTurno.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code IndicacionPrestacionTurno}
 * (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class IndicacionPrestacionTurnoCriteria {

    private UUIDFilter id;
    private UUIDFilter turnoId;
    private UUIDFilter indicacionPrestacionId;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code IndicacionPrestacionTurnoQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

}
