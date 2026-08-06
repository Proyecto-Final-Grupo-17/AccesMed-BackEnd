package com.accesmed.backend.Services.QueryServices.Filtering;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Filtro con operadores de rango, para campos comparables: fecha mínima/máxima
 * ({@code greaterThanOrEqual}/{@code lessThanOrEqual}), valores estrictamente mayores o
 * menores a un umbral ({@code greaterThan}/{@code lessThan}).
 *
 * @param <T> tipo del campo filtrado, debe ser {@link Comparable}
 */
@Getter
@Setter
@ToString(callSuper = true)
public class RangeFilter<T extends Comparable<? super T>> extends Filter<T> {

    private T greaterThan;
    private T lessThan;
    private T greaterThanOrEqual;
    private T lessThanOrEqual;

}
