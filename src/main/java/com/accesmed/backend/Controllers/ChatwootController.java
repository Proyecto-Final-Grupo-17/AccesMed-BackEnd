package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.ChatwootApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST del webhook del Agent Bot de Chatwoot. Recibe el body crudo (hace falta tal
 * cual para verificar la firma HMAC), delega en {@link ChatwootApp} y responde 200 de inmediato.
 */
@Slf4j
@RestController
@RequestMapping("/accesmed-api/Chatwoot")
@RequiredArgsConstructor
public class ChatwootController {

    //region ========== Dependencias o inyecciones ==========

    private final ChatwootApp chatwootApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Recibe un evento del Agent Bot de Chatwoot. El procesamiento (consulta a Flowise y respuesta)
     * es asíncrono: este método solo valida la firma y encola el trabajo.
     *
     * @param cuerpoCrudo {@code String} body del request sin parsear
     * @param firma {@code String} header {@code X-Chatwoot-Signature} ({@code sha256=<hex>})
     * @param timestamp {@code String} header {@code X-Chatwoot-Timestamp} (epoch en segundos)
     * @return {@code ResponseEntity<Void>} sin contenido con status 200
     */
    @PostMapping("/Webhook")
    public ResponseEntity<Void> receiveMensajeChatwoot(
            @RequestBody String cuerpoCrudo,
            @RequestHeader(value = "X-Chatwoot-Signature", required = false) String firma,
            @RequestHeader(value = "X-Chatwoot-Timestamp", required = false) String timestamp) {

        log.info("Solicitud recibida: webhook de Chatwoot");

        chatwootApp.receiveMensajeChatwoot(cuerpoCrudo, firma, timestamp);

        return ResponseEntity.ok().build();

    }

    //endregion

}
