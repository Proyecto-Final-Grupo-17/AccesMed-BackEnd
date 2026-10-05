package com.accesmed.backend.Records.TipoIndicacionPrestacion.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code TipoIndicacionPrestacion} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional. No incluye {@code deletedAt}: el
 * {@code QueryService} excluye las bajas lógicas siempre, sin exponerlo como filtro.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class TipoIndicacionPrestacionCriteria {

    private UUIDFilter id;
    private StringFilter codigo;
    private StringFilter nombre;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code TipoIndicacionPrestacionQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

}
