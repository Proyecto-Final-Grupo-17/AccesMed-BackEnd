package com.accesmed.backend.Services.Utils;

/**
 * Normaliza números de teléfono para compararlos sin importar el formato. El canal chatbot
 * entrega el teléfono en E.164 (ej. {@code +5492617742682}), mientras que en la base puede estar
 * guardado con espacios, guiones o paréntesis; la comparación se hace siempre sobre los dígitos.
 */
public final class NormalizadorTelefono {

    //region ========== Constantes ==========

    private static final int MINIMO_DIGITOS = 7;
    private static final int MAXIMO_DIGITOS = 15;

    //endregion

    private NormalizadorTelefono() {
    }

    //region ========== Métodos ==========

    /**
     * Quita todo lo que no sea un dígito (espacios, guiones, paréntesis y el {@code +} inicial).
     *
     * @param numeroTelefono {@code String} número de teléfono en cualquier formato, o {@code null}
     * @return {@code String} el número con solo dígitos; vacío si el parámetro es {@code null}
     */
    public static String normalizarDigitos(String numeroTelefono) {

        if (numeroTelefono == null) {
            return "";
        }

        return numeroTelefono.replaceAll("\\D", "");

    }

    /**
     * Devuelve el número en formato E.164 simplificado: {@code +} seguido de los dígitos.
     *
     * @param numeroTelefono {@code String} número de teléfono en cualquier formato
     * @return {@code String} el número como {@code +<dígitos>}
     */
    public static String normalizarE164(String numeroTelefono) {

        return "+" + normalizarDigitos(numeroTelefono);

    }

    /**
     * Indica si el texto tiene una cantidad de dígitos propia de un teléfono (E.164 admite hasta 15).
     *
     * @param numeroTelefono {@code String} número de teléfono a verificar, o {@code null}
     * @return {@code boolean} {@code true} si tiene entre 7 y 15 dígitos
     */
    public static boolean esValido(String numeroTelefono) {

        int cantidadDigitos = normalizarDigitos(numeroTelefono).length();

        return cantidadDigitos >= MINIMO_DIGITOS && cantidadDigitos <= MAXIMO_DIGITOS;

    }

    //endregion

}
