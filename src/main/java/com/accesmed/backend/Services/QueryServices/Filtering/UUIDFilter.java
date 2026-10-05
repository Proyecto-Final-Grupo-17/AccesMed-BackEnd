package com.accesmed.backend.Services.QueryServices.Filtering;

import java.util.UUID;

/**
 * {@link Filter} reificado para identificadores {@link UUID}. Clase concreta (en vez de
 * usar {@code Filter<UUID>} directo) para que Spring resuelva el tipo genérico al bindear
 * los query params anidados y para que el esquema de OpenAPI sea legible.
 */
public class UUIDFilter extends Filter<UUID> {

}
