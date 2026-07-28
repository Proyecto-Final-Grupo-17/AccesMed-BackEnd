package com.accesmed.backend.Controllers.Errors;

import java.time.Instant;
import java.util.List;

/**
 * Contrato único de error que ve el front, sin importar la causa (validación, regla de
 * negocio, recurso no encontrado o error inesperado).
 */
public record AccesMedError(
        Instant timestamp,
        int status,
        String codigo,
        String mensaje,
        List<String> errores,
        String path
) {

    /**
     * Construye un {@code AccesMedError} con el timestamp actual.
     *
     * @param status {@code int} status HTTP de la respuesta
     * @param codigo {@code String} código de negocio estable
     * @param mensaje {@code String} mensaje descriptivo del error
     * @param errores {@code List<String>} lista de errores (un solo elemento en errores simples)
     * @param path {@code String} endpoint que falló
     * @return {@code AccesMedError} el error listo para responder al front
     */
    public static AccesMedError of(int status, String codigo, String mensaje, List<String> errores, String path) {
        return new AccesMedError(Instant.now(), status, codigo, mensaje, errores, path);
    }

}
