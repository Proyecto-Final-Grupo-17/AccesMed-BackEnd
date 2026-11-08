package com.accesmed.backend.Records.Chatwoot.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Remitente (contacto) de un mensaje del webhook de Chatwoot. Se ignoran los campos que el
 * back no usa.
 *
 * @param id {@code Long} identificador del contacto en Chatwoot
 * @param name {@code String} nombre del contacto
 * @param phoneNumber {@code String} teléfono del contacto en E.164 (ej. {@code +5492617742682})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemitenteChatwootRequest(

        @JsonProperty("id")
        Long id,

        @JsonProperty("name")
        String name,

        @JsonProperty("phone_number")
        String phoneNumber

) {
}
