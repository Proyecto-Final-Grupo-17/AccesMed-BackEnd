package com.accesmed.backend.Records.Prestacion.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.EstadoPrestacionFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code Prestacion} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional. {@link ParameterObject} le indica a Swagger que
 * aplane los campos como parámetros de query individuales en vez de mostrarla como un
 * objeto anidado.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class PrestacionCriteria {

    private UUIDFilter id;
    private StringFilter codigo;
    private StringFilter nombre;
    private EstadoPrestacionFilter estadoActual;
    private UUIDFilter especialidadId;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

}
