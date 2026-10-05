package com.accesmed.backend.Services.QueryServices.Filtering;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * Filtro genérico de un campo para listados con filtrado dinámico: igualdad, pertenencia
 * a una lista y presencia/ausencia ({@code IS NOT NULL}/{@code IS NULL}). Las subclases
 * agregan los operadores propios de cada tipo de dato ({@link RangeFilter} para rangos,
 * {@link StringFilter} para texto parcial).
 *
 * <p>Vendorizado a partir del mecanismo de filtros de JHipster
 * ({@code tech.jhipster:jhipster-framework}), reimplementado como clase propia del
 * proyecto para no sumar esa dependencia completa.</p>
 *
 * @param <T> tipo del campo filtrado
 */
@Getter
@Setter
@ToString
public class Filter<T> implements Serializable {

    private T equals;
    private T notEquals;
    private List<T> in;
    private List<T> notIn;
    private Boolean specified;

}
