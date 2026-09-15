package com.accesmed.backend.Records.Plan.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.EstadoPlanFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code Plan} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional, incluido {@code obraSocialId}.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class PlanCriteria {

    private UUIDFilter id;
    private StringFilter codigo;
    private StringFilter nombre;
    private EstadoPlanFilter estadoActual;
    private UUIDFilter obraSocialId;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code PlanQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

}
