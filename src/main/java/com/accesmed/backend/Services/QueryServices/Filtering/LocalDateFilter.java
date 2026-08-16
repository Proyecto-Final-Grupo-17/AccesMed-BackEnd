package com.accesmed.backend.Services.QueryServices.Filtering;

import java.time.LocalDate;

/**
 * {@link RangeFilter} reificado para fechas {@link LocalDate} sin componente horario (ej.
 * {@code AgendaDia.fecha}).
 */
public class LocalDateFilter extends RangeFilter<LocalDate> {

}
