package com.accesmed.backend.Records.Medico.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code Medico} (ver
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
public class MedicoCriteria {

    private UUIDFilter id;
    private StringFilter matricula;
    private StringFilter dni;
    private StringFilter nombre;
    private StringFilter apellido;
    private StringFilter email;
    private UUIDFilter especialidadId;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

}
