package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Services.Utils.NormalizadorTelefono;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de la tanda en curso del canal chatbot, por teléfono del paciente (diseño en
 * {@code Docs/Diseno-Chatbot.md §3.4.6} y {@code §4.7}). {@code ChatwootApp} abre la tanda al
 * consultar a Flowise y la cierra al responder; mientras tanto {@code AgenteApp} avisa cuando el
 * agente pide una acción de escritura.
 * <p>
 * Resuelve una sola cosa: los <b>avisos de espera de la tanda</b>. De herramienta (escrituras y
 * consultas que tardan): uno por tipo de acción y como máximo {@value #MAX_AVISOS_HERRAMIENTA} por
 * tanda. De reintento: uno, cuando el agente llama por segunda vez a la verificación; a partir de ahí
 * los avisos de herramienta no se repiten. Solo con la tanda abierta: una llamada desde la UI de
 * Flowise no avisa. La verificación de las respuestas del agente ya no vive acá: la hace el flow contra el back con
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

    /**
     * Tope de avisos de herramienta (escrituras y consultas) por tanda, para no saturar al paciente.
     */
    private static final int MAX_AVISOS_HERRAMIENTA = 2;

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
            estadoTanda.accionesAvisadas.clear();
            estadoTanda.verificaciones = 0;
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
     * Registra que el agente pidió una acción (escritura o consulta) e indica si corresponde mandar su
     * aviso de espera: solo con la tanda abierta (una llamada desde la UI de Flowise no avisa), una vez
     * por tipo de acción, hasta {@value #MAX_AVISOS_HERRAMIENTA} avisos por tanda y nunca después de que
     * el agente entró en reintento de verificación.
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @param accionAgente {@code AccionAgente} acción pedida
     * @return {@code OptionalLong} la conversación a la que hay que avisar, o vacío si no corresponde
     */
    public OptionalLong registerInicioAccion(String numeroTelefono, AccionAgente accionAgente) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null) {
            return OptionalLong.empty();
        }

        synchronized (estadoTanda) {
            if (!estadoTanda.abierta || estadoTanda.verificaciones >= 2
                    || estadoTanda.accionesAvisadas.contains(accionAgente)
                    || estadoTanda.accionesAvisadas.size() >= MAX_AVISOS_HERRAMIENTA) {
                return OptionalLong.empty();
            }
            estadoTanda.accionesAvisadas.add(accionAgente);
            log.debug("Aviso de espera para {}: conversación={}", accionAgente, estadoTanda.conversationId);
            return OptionalLong.of(estadoTanda.conversationId);
        }

    }

    /**
     * Registra que el agente llamó a la verificación de su respuesta e indica si corresponde el aviso
     * de reintento: la segunda llamada de la misma tanda es el agente rehaciendo su respuesta (el aviso
     * sale una sola vez). Solo con la tanda abierta.
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @return {@code OptionalLong} la conversación a la que hay que avisar, o vacío si no corresponde
     */
    public OptionalLong registerVerificacion(String numeroTelefono) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null) {
            return OptionalLong.empty();
        }

        synchronized (estadoTanda) {
            if (!estadoTanda.abierta) {
                return OptionalLong.empty();
            }
            estadoTanda.verificaciones++;
            if (estadoTanda.verificaciones != 2) {
                return OptionalLong.empty();
            }
            log.debug("Aviso de reintento de verificación: conversación={}", estadoTanda.conversationId);
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

    /**
     * Obtiene el estado de la tanda de un teléfono.
     *
     * @param numeroTelefono {@code String} teléfono del paciente, o {@code null}
     * @return {@code EstadoTanda} el estado, o {@code null} si no hay teléfono o nunca abrió una tanda
     */
    private EstadoTanda findEstadoTanda(String numeroTelefono) {

        if (numeroTelefono == null || numeroTelefono.isBlank()) {
            return null;
        }
        EstadoTanda estadoTanda = estadosPorTelefono.get(buildClave(numeroTelefono));
        return estadoTanda;

    }

    //endregion

    /**
     * Estado de un teléfono. Se lee y modifica con su propio monitor tomado.
     */
    private static final class EstadoTanda {

        private long conversationId;
        private boolean abierta;
        private final Set<AccionAgente> accionesAvisadas = EnumSet.noneOf(AccionAgente.class);
        private int verificaciones;
        private Instant ultimoUso = Instant.now();

    }

}
