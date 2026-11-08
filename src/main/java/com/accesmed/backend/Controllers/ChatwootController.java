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
 * Controller REST del webhook del Agent Bot de Chatwoot (contrato en
 * {@code Docs/Features/Chatbot.md §6}). La firma HMAC ya la verificó {@code ChatwootFirmaFilter};
 * recibe el body crudo, delega en {@link ChatwootApp} y responde 200 de inmediato.
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
     * Recibe un evento del Agent Bot de Chatwoot. El procesamiento (agrupación, consulta a Flowise y
     * respuesta) es asíncrono: este método solo encola el trabajo.
     *
     * @param cuerpoCrudo {@code String} body del request sin parsear
     * @param idEntrega {@code String} header {@code X-Chatwoot-Delivery} (id de entrega, solo para el log)
     * @return {@code ResponseEntity<Void>} sin contenido con status 200
     */
    @PostMapping("/Webhook")
    public ResponseEntity<Void> receiveMensajeChatwoot(
            @RequestBody String cuerpoCrudo,
            @RequestHeader(value = "X-Chatwoot-Delivery", required = false) String idEntrega) {

        log.info("Solicitud recibida: webhook de Chatwoot");

        chatwootApp.receiveMensajeChatwoot(cuerpoCrudo, idEntrega);

        return ResponseEntity.ok().build();

    }

    //endregion

}
