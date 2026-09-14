/**
 * Infraestructura de filtrado dinámico compartida por todos los {@code QueryService}: los
 * {@code Filter} genéricos que arma cada {@code <Entidad>Criteria}, {@link
 * com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService} (base que
 * traduce esos filtros a {@code Specification} vía el metamodelo estático de JPA) y {@link
 * com.accesmed.backend.Services.QueryServices.Filtering.PageResponse} (envelope de paginación
 * de los listados). Ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}.
 */
package com.accesmed.backend.Services.QueryServices.Filtering;
