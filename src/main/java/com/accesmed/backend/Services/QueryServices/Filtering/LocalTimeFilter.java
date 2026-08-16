package com.accesmed.backend.Services.QueryServices.Filtering;

import java.time.LocalTime;

/**
 * {@link RangeFilter} reificado para horas del día {@link LocalTime} sin componente de
 * fecha (ej. {@code AgendaHorariosDia.horaDesde}).
 */
public class LocalTimeFilter extends RangeFilter<LocalTime> {

}
