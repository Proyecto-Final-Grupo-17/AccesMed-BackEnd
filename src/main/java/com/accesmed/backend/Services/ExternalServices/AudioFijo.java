package com.accesmed.backend.Services.ExternalServices;

/**
 * Notas de voz pregrabadas del canal (OGG/Opus, la misma voz que las respuestas) que el back manda
 * en lugar de sintetizar en cada envío, solo cuando el paciente habló por audio. Viven en la carpeta
 * {@code accesmed.audio.fijos-dir}; los textos y cuándo se usa cada una están en
 * {@code Scripts/audios-fijos.md}. Sin el archivo, el canal manda el texto.
 */
public enum AudioFijo {

    /**
     * Primer mensaje de la ventana, si es un audio.
     */
    BIENVENIDA("bienvenida.ogg"),

    /**
     * Aviso inicial de la tanda, si el mensaje que la abre es un audio.
     */
    ESPERA("espera.ogg"),

    /**
     * Respuesta del agente con {@code —FIN—}.
     */
    DESPEDIDA("despedida.ogg"),

    /**
     * Respuesta del agente con {@code —HANDOFF—}.
     */
    DERIVACION("derivacion.ogg"),

    /**
     * El audio del paciente no se pudo transcribir.
     */
    AUDIO_NO_ENTENDIDO("audio-no-entendido.ogg"),

    /**
     * El audio del paciente supera el tamaño máximo.
     */
    AUDIO_LARGO("audio-largo.ogg");

    private final String nombreArchivo;

    AudioFijo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    /**
     * Devuelve el nombre del archivo del audio dentro de la carpeta de audios fijos.
     *
     * @return {@code String} nombre del archivo (ej. {@code bienvenida.ogg})
     */
    public String nombreArchivo() {
        return nombreArchivo;
    }

}
