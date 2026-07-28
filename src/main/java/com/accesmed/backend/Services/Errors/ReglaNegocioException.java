package com.accesmed.backend.Services.Errors;

/**
 * Excepción para un conflicto de regla de negocio (unicidad, estado inválido, etc.). Se
 * traduce a HTTP 409.
 */
public class ReglaNegocioException extends AccesMedException {

    /**
     * Construye la excepción de regla de negocio (HTTP 409).
     *
     * @param origen {@code Class<?>} clase que lanza la excepción, solo para diagnóstico
     * @param codigo {@code String} código de negocio estable
     * @param mensaje {@code String} mensaje descriptivo del error
     */
    public ReglaNegocioException(Class<?> origen, String codigo, String mensaje) {
        super(origen, codigo, mensaje, 409);
    }

}
