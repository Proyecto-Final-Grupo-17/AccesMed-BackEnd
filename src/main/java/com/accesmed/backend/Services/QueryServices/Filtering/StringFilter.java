package com.accesmed.backend.Services.QueryServices.Filtering;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Filtro de texto: agrega búsqueda por contención ({@code LIKE '%valor%'}, sin distinguir
 * mayúsculas/minúsculas) a los operadores heredados de {@link Filter}.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class StringFilter extends Filter<String> {

    private String contains;

}
