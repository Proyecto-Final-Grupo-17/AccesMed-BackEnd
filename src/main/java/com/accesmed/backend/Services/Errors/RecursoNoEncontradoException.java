package com.accesmed.backend.Services.Errors;

/**
 * Excepción para cuando un recurso solicitado no existe o no está activo. Se traduce a
 * HTTP 404.
 */
public class RecursoNoEncontradoException extends AccesMedException {

    /**
     * Construye la excepción de recurso no encontrado (HTTP 404).
     *
     * @param origen {@code Class<?>} clase que lanza la excepción, solo para diagnóstico
     * @param codigo {@code String} código de negocio estable
     * @param mensaje {@code String} mensaje descriptivo del error
     */
    public RecursoNoEncontradoException(Class<?> origen, String codigo, String mensaje) {
        super(origen, codigo, mensaje, 404);
    }

}
