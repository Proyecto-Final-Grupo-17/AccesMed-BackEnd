package com.accesmed.backend.Records.Chatwoot.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload del webhook del Agent Bot de Chatwoot para el evento {@code message_created}. Solo
 * mapea los campos que usa el pasamanos; el resto se ignora. No lleva Bean Validation: se
 * parsea a mano a partir del body crudo (necesario para verificar la firma HMAC).
 *
 * @param event {@code String} tipo de evento (se procesa solo {@code message_created})
 * @param content {@code String} texto del mensaje
 * @param messageType {@code String} tipo de mensaje ({@code incoming} = del paciente; Chatwoot
 *        también puede mandarlo numérico, donde {@code 0} es entrante)
 * @param privado {@code Boolean} {@code true} si es una nota privada de un agente
 * @param conversation {@code ConversacionChatwootRequest} conversación del mensaje
 * @param sender {@code RemitenteChatwootRequest} contacto que escribió
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiveMensajeChatwootRequest(

        @JsonProperty("event")
        String event,

        @JsonProperty("content")
        String content,

        @JsonProperty("message_type")
        String messageType,

        @JsonProperty("private")
        Boolean privado,

        @JsonProperty("conversation")
        ConversacionChatwootRequest conversation,

        @JsonProperty("sender")
        RemitenteChatwootRequest sender

) {
}
