package com.accesmed.backend.Records.IndicacionPrestacion.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.BooleanFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code IndicacionPrestacion} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional. No incluye {@code fechaInicioVigencia}/
 * {@code fechaFinVigencia}: {@code IndicacionPrestacion} no tiene baja lógica, así que el
 * {@code QueryService} excluye siempre las no vigentes (equivalente al {@code deletedAt
 * IS NULL} de las demás entidades), sin exponer la vigencia como filtro.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class IndicacionPrestacionCriteria {

    private UUIDFilter id;
    private StringFilter nombre;
    private BooleanFilter requiereValidacion;
    private UUIDFilter prestacionId;
    private UUIDFilter tipoIndicacionPrestacionId;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code IndicacionPrestacionQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

}
