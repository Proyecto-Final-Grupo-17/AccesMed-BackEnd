package com.accesmed.backend.Agente.Controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de entrada del canal WhatsApp/Flowise para los Custom Tools de Turno
 * (Solicitar / Reprogramar / Cancelar / Confirmar). Reutiliza el mismo {@code Application}
 * y {@code Services} que el resto del sistema; lo único propio del agente es la forma de
 * request/response que le conviene a Flowise.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Agente/Turno")
public class TurnoAgenteController {

}
