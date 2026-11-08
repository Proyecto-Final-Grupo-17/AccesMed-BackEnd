package com.accesmed.backend.Services.Utils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normaliza el texto de una búsqueda por parecido (canal chatbot, {@code Docs/Diseno-Chatbot.md
 * §3.4.4}): minúsculas, sin tildes ni signos, y sin las palabras que no ayudan a encontrar nada
 * ("dr", "la", "de"). El resultado se compara contra {@code lower(unaccent(columna))} en la base.
 */
public final class NormalizadorBusqueda {

    //region ========== Constantes ==========

    /**
     * Palabras que se descartan de la consulta: títulos y artículos/preposiciones frecuentes.
     */
    private static final Set<String> PALABRAS_IGNORADAS = Set.of(
            "dr", "dra", "doctor", "doctora", "doc", "el", "la", "los", "las", "de", "del", "con", "y", "un", "una");

    //endregion

    private NormalizadorBusqueda() {
    }

    //region ========== Métodos ==========

    /**
     * Pasa el texto a minúsculas, le quita las tildes y reemplaza todo lo que no sea letra o dígito
     * por un espacio (colapsando los espacios repetidos).
     *
     * @param texto {@code String} texto libre, o {@code null}
     * @return {@code String} el texto normalizado; vacío si el parámetro es {@code null}
     */
    public static String normalizarTexto(String texto) {

        if (texto == null) {
            return "";
        }

        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String textoNormalizado = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return textoNormalizado;

    }

    /**
     * Separa el texto normalizado en palabras, descartando las ignoradas. Si todas son ignoradas,
     * devuelve las palabras originales (para no quedarse sin consulta).
     *
     * @param texto {@code String} texto libre, o {@code null}
     * @return {@code List<String>} palabras normalizadas para buscar (vacía si no hay texto)
     */
    public static List<String> findPalabrasBusqueda(String texto) {

        String textoNormalizado = normalizarTexto(texto);
        if (textoNormalizado.isEmpty()) {
            return List.of();
        }

        List<String> palabras = Arrays.asList(textoNormalizado.split(" "));
        List<String> palabrasUtiles = palabras.stream()
                .filter(palabra -> !PALABRAS_IGNORADAS.contains(palabra))
                .toList();

        List<String> palabrasBusqueda = palabrasUtiles.isEmpty() ? palabras : palabrasUtiles;
        return palabrasBusqueda;

    }

    //endregion

}
