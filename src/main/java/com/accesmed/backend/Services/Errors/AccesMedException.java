package com.accesmed.backend.Services.Errors;

import lombok.Getter;

/**
 * Excepción base no chequeada del proyecto. Nunca se serializa ni llega al front: el
 * {@code GlobalExceptionHandler} arma un {@code AccesMedError} de cero a partir de ella.
 */
@Getter
public abstract class AccesMedException extends RuntimeException {

    private final String codigo;
    private final int httpStatus;
    private final String origen;

    /**
     * Construye la excepción con su código de negocio, mensaje y status HTTP asociado.
     *
     * @param origen {@code Class<?>} clase que lanza la excepción, solo para diagnóstico
     * @param codigo {@code String} código de negocio estable (ej. {@code "PRESTACION_CODIGO_DUPLICADO"})
     * @param mensaje {@code String} mensaje descriptivo del error
     * @param httpStatus {@code int} status HTTP que debe responder el {@code GlobalExceptionHandler}
     */
    protected AccesMedException(Class<?> origen, String codigo, String mensaje, int httpStatus) {

        super(mensaje);

        this.origen = origen.getSimpleName();
        this.codigo = codigo;
        this.httpStatus = httpStatus;

    }

}
