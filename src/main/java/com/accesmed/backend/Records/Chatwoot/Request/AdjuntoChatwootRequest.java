package com.accesmed.backend.Records.Chatwoot.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Adjunto de un mensaje del webhook de Chatwoot. Solo interesa el tipo: el archivo queda en
 * Chatwoot y nunca se le pasa a Flowise.
 *
 * @param fileType {@code String} tipo del adjunto ({@code image}, {@code audio}, {@code video}, {@code file}, ...)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdjuntoChatwootRequest(

        @JsonProperty("file_type")
        String fileType

) {
}
