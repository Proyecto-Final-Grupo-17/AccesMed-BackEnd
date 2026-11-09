package com.accesmed.backend.Records.Chatwoot.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Adjunto de un mensaje del webhook de Chatwoot. Interesa el tipo y la URL de descarga: los
 * audios se descargan para transcribirlos ({@code Docs/Diseno-Chatbot.md §5}); imágenes y archivos
 * quedan en Chatwoot para el equipo y nunca se le pasan a Flowise.
 *
 * @param fileType {@code String} tipo del adjunto ({@code image}, {@code audio}, {@code video}, {@code file}, ...)
 * @param dataUrl {@code String} URL de descarga del archivo en Chatwoot
 * @param fileSize {@code Long} tamaño del archivo en bytes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdjuntoChatwootRequest(

        @JsonProperty("file_type")
        String fileType,

        @JsonProperty("data_url")
        String dataUrl,

        @JsonProperty("file_size")
        Long fileSize

) {
}
