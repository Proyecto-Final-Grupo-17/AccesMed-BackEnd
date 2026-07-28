package com.accesmed.backend.Services.Errors;

import lombok.Getter;

import java.util.List;

/**
 * Excepción para acumular varios errores de negocio y devolverlos juntos en una sola
 * respuesta. Se traduce a HTTP 422.
 */
@Getter
public class ValidacionException extends AccesMedException {

    private final List<String> errores;

    /**
     * Construye la excepción de validación con la lista completa de errores acumulados.
     *
     * @param origen {@code Class<?>} clase que lanza la excepción, solo para diagnóstico
     * @param errores {@code List<String>} errores de negocio acumulados
     */
    public ValidacionException(Class<?> origen, List<String> errores) {

        super(origen, "VALIDACION", "La solicitud tiene errores de validación.", 422);

        this.errores = errores;

    }

}
