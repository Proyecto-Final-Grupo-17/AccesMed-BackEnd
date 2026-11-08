package com.accesmed.backend.Records.Chatwoot.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadatos de la conversación de Chatwoot; se usan para el teléfono cuando el {@code sender}
 * del mensaje no lo trae.
 *
 * @param sender {@code RemitenteChatwootRequest} contacto dueño de la conversación
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetaChatwootRequest(

        @JsonProperty("sender")
        RemitenteChatwootRequest sender

) {
}
