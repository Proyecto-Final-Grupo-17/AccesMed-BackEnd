package com.accesmed.backend.Records.Turno.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.EstadoTurnoFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.ZonedDateTimeFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code Turno} (ver
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
public class TurnoCriteria {

    private UUIDFilter pacienteId;
    private UUIDFilter medicoId;
    private UUIDFilter prestacionId;
    private EstadoTurnoFilter estadoActual;
    private ZonedDateTimeFilter fechaHoraInicio;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

}
