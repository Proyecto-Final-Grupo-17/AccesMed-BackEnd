package com.accesmed.backend.Services.Utils;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Agrupa los elementos que llegan en ráfaga bajo una misma clave y los entrega juntos, como una
 * tanda, cuando se cumple la primera de tres condiciones: pasó la {@code espera} sin elementos
 * nuevos, se acumularon {@code maxElementos}, o pasó la {@code esperaMaxima} desde el primero.
 * Mientras una tanda de la clave se está procesando, lo nuevo espera a la siguiente: nunca hay
 * dos tandas de la misma clave en paralelo. El estado vive en memoria (se pierde al reiniciar).
 *
 * @param <T> tipo de los elementos agrupados
 */
@Slf4j
public class AgrupadorMensajes<T> implements AutoCloseable {

    //region ========== Dependencias o inyecciones ==========

    private final Duration espera;
    private final Duration esperaMaxima;
    private final int maxElementos;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Map<Long, Grupo<T>> grupos = new HashMap<>();

    //endregion

    /**
     * Construye el agrupador con su propio hilo de temporizadores.
     *
     * @param espera {@code Duration} silencio que cierra la tanda
     * @param esperaMaxima {@code Duration} tope desde el primer elemento de la tanda
     * @param maxElementos {@code int} cantidad que cierra la tanda en el acto
     * @param executor {@code Executor} hilos donde corre el procesamiento de cada tanda
     */
    public AgrupadorMensajes(Duration espera, Duration esperaMaxima, int maxElementos, Executor executor) {

        this.espera = espera;
        this.esperaMaxima = esperaMaxima;
        this.maxElementos = maxElementos;
        this.executor = executor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread hilo = new Thread(runnable, "agrupador-mensajes");
            hilo.setDaemon(true);
            return hilo;
        });

    }

    //region ========== Métodos ==========

    /**
     * Suma un elemento a la tanda de la clave y reprograma sus temporizadores. Si con él se llega
     * a {@code maxElementos}, la tanda se cierra en el acto.
     *
     * @param clave {@code Long} clave de agrupación (ej. id de conversación)
     * @param elemento {@code T} elemento a agrupar
     * @param procesador {@code Consumer<List<T>>} qué hacer con la tanda cerrada
     * @return {@code boolean} {@code true} si el elemento abrió una tanda nueva (la clave no tenía
     *         pendientes antes de agregarlo); {@code false} si se sumó a una tanda ya abierta
     */
    public synchronized boolean addElemento(Long clave, T elemento, Consumer<List<T>> procesador) {

        Grupo<T> grupo = grupos.computeIfAbsent(clave, claveNueva -> new Grupo<>());
        boolean abreTandaNueva = grupo.pendientes.isEmpty();
        grupo.procesador = procesador;
        grupo.pendientes.add(elemento);

        if (grupo.pendientes.size() >= maxElementos) {
            cancelTemporizadores(grupo);
            closeTanda(clave, grupo);
            return abreTandaNueva;
        }

        if (grupo.temporizadorEspera != null) {
            grupo.temporizadorEspera.cancel(false);
        }
        grupo.temporizadorEspera = scheduler.schedule(() -> onTemporizador(clave), espera.toMillis(), TimeUnit.MILLISECONDS);
        if (grupo.temporizadorMaximo == null) {
            grupo.temporizadorMaximo = scheduler.schedule(() -> onTemporizador(clave), esperaMaxima.toMillis(), TimeUnit.MILLISECONDS);
        }
        return abreTandaNueva;

    }

    /**
     * Detiene los temporizadores. Las tandas que no se cerraron se descartan.
     */
    @Override
    public void close() {

        scheduler.shutdownNow();

    }

    //endregion

    //region ========== Helpers privados ==========

    /**
     * Venció un temporizador de la clave: cierra su tanda.
     *
     * @param clave {@code Long} clave cuyo temporizador venció
     */
    private synchronized void onTemporizador(Long clave) {

        Grupo<T> grupo = grupos.get(clave);
        if (grupo == null || grupo.pendientes.isEmpty()) {
            return;
        }
        cancelTemporizadores(grupo);
        closeTanda(clave, grupo);

    }

    /**
     * Cierra la tanda: si la clave ya tiene una en proceso la deja lista para cuando termine; si
     * no, la saca de pendientes y la manda a procesar.
     *
     * @param clave {@code Long} clave de la tanda
     * @param grupo {@code Grupo<T>} estado de la clave
     */
    private void closeTanda(Long clave, Grupo<T> grupo) {

        if (grupo.procesando) {
            grupo.listaParaProcesar = true;
            return;
        }

        List<T> tanda = new ArrayList<>(grupo.pendientes);
        grupo.pendientes.clear();
        grupo.listaParaProcesar = false;
        grupo.procesando = true;
        Consumer<List<T>> procesador = grupo.procesador;

        try {
            executor.execute(() -> {
                try {
                    procesador.accept(tanda);
                } catch (RuntimeException excepcion) {
                    log.error("Falló el procesamiento de la tanda de la clave {}", clave, excepcion);
                } finally {
                    onTandaProcesada(clave);
                }
            });
        } catch (RejectedExecutionException excepcion) {
            //Executor saturado: se descarta la tanda para no dejar la clave trabada en "procesando"
            log.error("Executor saturado: se descarta la tanda de la clave {} ({} elementos)", clave, tanda.size(), excepcion);
            grupo.procesando = false;
        }

    }

    /**
     * Terminó la tanda de la clave: si mientras tanto se cerró otra, la procesa; si no quedan
     * pendientes, libera la clave.
     *
     * @param clave {@code Long} clave cuya tanda terminó
     */
    private synchronized void onTandaProcesada(Long clave) {

        Grupo<T> grupo = grupos.get(clave);
        if (grupo == null) {
            return;
        }
        grupo.procesando = false;

        if (grupo.listaParaProcesar && !grupo.pendientes.isEmpty()) {
            closeTanda(clave, grupo);
        } else if (grupo.pendientes.isEmpty()) {
            grupos.remove(clave);
        }

    }

    /**
     * Cancela los temporizadores de un grupo.
     *
     * @param grupo {@code Grupo<T>} grupo a limpiar
     */
    private void cancelTemporizadores(Grupo<T> grupo) {

        if (grupo.temporizadorEspera != null) {
            grupo.temporizadorEspera.cancel(false);
            grupo.temporizadorEspera = null;
        }
        if (grupo.temporizadorMaximo != null) {
            grupo.temporizadorMaximo.cancel(false);
            grupo.temporizadorMaximo = null;
        }

    }

    /**
     * Estado de una clave: elementos pendientes, temporizadores y si tiene una tanda en proceso.
     *
     * @param <T> tipo de los elementos
     */
    private static final class Grupo<T> {

        private final List<T> pendientes = new ArrayList<>();
        private ScheduledFuture<?> temporizadorEspera;
        private ScheduledFuture<?> temporizadorMaximo;
        private boolean procesando;
        private boolean listaParaProcesar;
        private Consumer<List<T>> procesador;

    }

    //endregion

}
