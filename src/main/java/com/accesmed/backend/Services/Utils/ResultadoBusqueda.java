package com.accesmed.backend.Services.Utils;

import java.util.List;

/**
 * Resultado de una búsqueda por parecido: las entidades encontradas y cómo se encontraron.
 *
 * @param resultados {@code List<T>} entidades encontradas (vacía si no hay ninguna)
 * @param coincidencia {@code CoincidenciaBusqueda} {@code EXACTA} por palabras o {@code APROXIMADA} por similitud
 * @param <T> tipo de la entidad buscada
 */
public record ResultadoBusqueda<T>(
        List<T> resultados,
        CoincidenciaBusqueda coincidencia
) {

    /**
     * Crea un resultado sin coincidencias.
     *
     * @param <T> tipo de la entidad buscada
     * @return {@code ResultadoBusqueda<T>} resultado vacío
     */
    public static <T> ResultadoBusqueda<T> vacio() {

        ResultadoBusqueda<T> resultadoVacio = new ResultadoBusqueda<>(List.of(), CoincidenciaBusqueda.EXACTA);
        return resultadoVacio;

    }

}
