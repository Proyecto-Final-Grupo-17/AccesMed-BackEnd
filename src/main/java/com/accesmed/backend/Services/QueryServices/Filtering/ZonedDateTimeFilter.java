package com.accesmed.backend.Services.QueryServices.Filtering;

import java.time.ZonedDateTime;

/**
 * {@link RangeFilter} reificado para instantes con zona horaria {@link ZonedDateTime} (ej.
 * {@code AgendaMedico.fechaHoraInicioVigencia}/{@code fechaHoraFinVigencia}). No estaba en
 * la lista original de filtros de la Fase B: los campos de vigencia de {@code AgendaMedico}
 * son {@code ZonedDateTime}, no {@code Instant}, así que {@link InstantFilter} no aplica.
 */
public class ZonedDateTimeFilter extends RangeFilter<ZonedDateTime> {

}
