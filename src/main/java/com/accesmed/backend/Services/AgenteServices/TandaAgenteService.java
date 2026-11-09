package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Services.Utils.NormalizadorTelefono;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de la tanda en curso del canal chatbot, por teléfono del paciente (diseño en
 * {@code Docs/Diseno-Chatbot.md §3.4.6} y {@code §4.7}). {@code ChatwootApp} abre la tanda al
 * consultar a Flowise y la cierra al responder; mientras tanto {@code AgenteApp} avisa cuando el
 * agente pide una acción de escritura.
 * <p>
 * Resuelve una sola cosa: el <b>aviso de espera de las escrituras</b> (uno por tanda, al primer
 * pedido que lo amerita, y solo con la tanda abierta: una llamada desde la UI de Flowise no avisa).
 * La verificación de las respuestas del agente ya no vive acá: la hace el flow contra el back con
 * {@code POST /Agente/Verificacion} ({@code VerificacionAgenteService}, {@code Docs/Diseno-Chatbot.md §4.3}).
 * <p>
 * Vive en memoria: supone una sola instancia del back, igual que el resto del estado del canal.
 */
@Slf4j
@Service
public class TandaAgenteService {

    //region ========== Constantes ==========

    /**
     * Tiempo sin uso después del cual se borra el estado de un teléfono.
     */
    private static final Duration VENCIMIENTO_ESTADO = Duration.ofHours(2);

    //endregion

    //region ========== Atributos ==========

    /**
     * Estado de cada teléfono, por clave (teléfono con solo dígitos).
     */
    private final Map<String, EstadoTanda> estadosPorTelefono = new ConcurrentHashMap<>();

    //endregion

    //region ========== Métodos ==========

    /**
     * Abre la tanda de un teléfono antes de consultar a Flowise.
     *
     * @param numeroTelefono {@code String} teléfono del paciente, en cualquier formato
     * @param conversationId {@code long} conversación de Chatwoot (destino del aviso de espera)
     */
    public void openTanda(String numeroTelefono, long conversationId) {

        EstadoTanda estadoTanda = estadosPorTelefono.computeIfAbsent(buildClave(numeroTelefono), clave -> new EstadoTanda());

        synchronized (estadoTanda) {
            estadoTanda.conversationId = conversationId;
            estadoTanda.abierta = true;
            estadoTanda.avisoEsperaEnviado = false;
            estadoTanda.ultimoUso = Instant.now();
        }

    }

    /**
     * Cierra la tanda de un teléfono: las llamadas que lleguen después ya no avisan.
     *
     * @param numeroTelefono {@code String} teléfono del paciente, en cualquier formato
     */
    public void closeTanda(String numeroTelefono) {

        EstadoTanda estadoTanda = estadosPorTelefono.get(buildClave(numeroTelefono));
        if (estadoTanda == null) {
            return;
        }

        synchronized (estadoTanda) {
            estadoTanda.abierta = false;
        }

    }

    /**
     * Registra que el agente pidió una acción e indica si corresponde mandar el aviso de espera: uno
     * solo por tanda, y solo con la tanda abierta (una llamada desde la UI de Flowise no avisa).
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @param accionAgente {@code AccionAgente} acción pedida
     * @return {@code OptionalLong} la conversación a la que hay que avisar, o vacío si no corresponde
     */
    public OptionalLong registerInicioAccion(String numeroTelefono, AccionAgente accionAgente) {

        if (numeroTelefono == null || numeroTelefono.isBlank()) {
            return OptionalLong.empty();
        }

        EstadoTanda estadoTanda = estadosPorTelefono.get(buildClave(numeroTelefono));
        if (estadoTanda == null) {
            return OptionalLong.empty();
        }

        synchronized (estadoTanda) {
            if (!estadoTanda.abierta || estadoTanda.avisoEsperaEnviado) {
                return OptionalLong.empty();
            }
            estadoTanda.avisoEsperaEnviado = true;
            log.debug("Aviso de espera para {}: conversación={}", accionAgente, estadoTanda.conversationId);
            return OptionalLong.of(estadoTanda.conversationId);
        }

    }

    /**
     * Borra el estado de los teléfonos sin uso reciente. Corre cada 30 minutos.
     */
    @Scheduled(fixedDelayString = "PT30M")
    public void removeEstadosVencidos() {

        Instant limite = Instant.now().minus(VENCIMIENTO_ESTADO);
        estadosPorTelefono.entrySet().removeIf(entrada -> {
            EstadoTanda estadoTanda = entrada.getValue();
            synchronized (estadoTanda) {
                return !estadoTanda.abierta && estadoTanda.ultimoUso.isBefore(limite);
            }
        });

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Arma la clave del mapa: el teléfono con solo dígitos (el canal y la tool lo pueden escribir distinto).
     *
     * @param numeroTelefono {@code String} teléfono en cualquier formato
     * @return {@code String} solo los dígitos
     */
    private String buildClave(String numeroTelefono) {

        String clave = NormalizadorTelefono.normalizarDigitos(numeroTelefono);
        return clave;

    }

    //endregion

    /**
     * Estado de un teléfono. Se lee y modifica con su propio monitor tomado.
     */
    private static final class EstadoTanda {

        private long conversationId;
        private boolean abierta;
        private boolean avisoEsperaEnviado;
        private Instant ultimoUso = Instant.now();

    }

}
