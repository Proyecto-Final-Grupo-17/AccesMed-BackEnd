package com.accesmed.backend.Services.QueryServices.Filtering;

import java.time.Instant;

/**
 * {@link RangeFilter} reificado para fechas/horas {@link Instant} (campos de auditoría
 * como {@code createdDate}/{@code lastModifiedDate}, o cualquier timestamp del dominio).
 */
public class InstantFilter extends RangeFilter<Instant> {

}
