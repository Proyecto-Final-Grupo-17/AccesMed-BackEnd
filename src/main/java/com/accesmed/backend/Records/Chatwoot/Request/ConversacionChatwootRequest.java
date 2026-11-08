package com.accesmed.backend.Records.Chatwoot.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Conversación de Chatwoot a la que pertenece el mensaje del webhook.
 *
 * @param id {@code Long} identificador de la conversación
 * @param status {@code String} estado de la conversación ({@code pending} = la atiende el bot,
 *        {@code open} = la tomó una persona)
 * @param meta {@code MetaChatwootRequest} metadatos de la conversación
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversacionChatwootRequest(

        @JsonProperty("id")
        Long id,

        @JsonProperty("status")
        String status,

        @JsonProperty("meta")
        MetaChatwootRequest meta

) {
}
