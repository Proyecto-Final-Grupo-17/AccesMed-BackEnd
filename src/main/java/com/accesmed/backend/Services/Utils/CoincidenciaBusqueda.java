package com.accesmed.backend.Services.Utils;

/**
 * Cómo se encontraron los resultados de una búsqueda por parecido (canal chatbot,
 * Diseno-Chatbot §4.5): el agente distingue "es este" de "ese no existe; ¿quisiste decir…?".
 */
public enum CoincidenciaBusqueda {

    /**
     * Cada palabra buscada está contenida en el nombre (sin distinguir mayúsculas ni tildes).
     */
    EXACTA,

    /**
     * No hubo coincidencia por palabras: los resultados salen del fallback por similitud de
     * trigramas ({@code pg_trgm}) y pueden ser otra cosa parecida.
     */
    APROXIMADA

}
