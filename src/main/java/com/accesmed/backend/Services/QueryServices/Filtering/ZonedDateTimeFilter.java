package com.accesmed.backend.Services.QueryServices.Filtering;

import java.time.ZonedDateTime;

/**
 * {@link RangeFilter} reificado para instantes con zona horaria {@link ZonedDateTime} (ej.
 * {@code Turno.fechaHoraInicio}). No estaba en la lista original de filtros de la Fase B;
 * {@link InstantFilter} no aplica cuando el campo del dominio es {@code ZonedDateTime}.
 */
public class ZonedDateTimeFilter extends RangeFilter<ZonedDateTime> {

}
