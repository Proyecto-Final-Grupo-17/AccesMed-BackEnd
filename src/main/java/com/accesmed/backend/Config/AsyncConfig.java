package com.accesmed.backend.Config;

import com.accesmed.backend.Records.Chatwoot.Request.ReceiveMensajeChatwootRequest;
import com.accesmed.backend.Services.Utils.AgrupadorMensajes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Infraestructura asíncrona del pasamanos de Chatwoot: el webhook responde 200 de inmediato, los
 * mensajes en ráfaga se agrupan por conversación y la consulta a Flowise (que puede tardar decenas
 * de segundos) corre en el executor, para que Chatwoot no reintente ni dé timeout.
 */
@Configuration
public class AsyncConfig {

    //region ========== Métodos ==========

    /**
     * Executor acotado para procesar los mensajes de Chatwoot fuera del hilo del request.
     *
     * @return {@code ThreadPoolTaskExecutor} executor con cola acotada
     */
    @Bean(name = "chatwootTaskExecutor")
    public ThreadPoolTaskExecutor chatwootTaskExecutor() {

        ThreadPoolTaskExecutor chatwootTaskExecutor = new ThreadPoolTaskExecutor();
        chatwootTaskExecutor.setThreadNamePrefix("chatwoot-");
        chatwootTaskExecutor.setCorePoolSize(2);
        chatwootTaskExecutor.setMaxPoolSize(8);
        chatwootTaskExecutor.setQueueCapacity(100);
        chatwootTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        chatwootTaskExecutor.setAwaitTerminationSeconds(30);
        return chatwootTaskExecutor;

    }

    /**
     * Agrupador de los mensajes entrantes de Chatwoot por conversación (contrato en
     * {@code Docs/Features/Chatbot.md §6.C}). Cada tanda se procesa en {@code chatwootTaskExecutor}.
     *
     * @param chatwootTaskExecutor {@code Executor} hilos donde se procesa cada tanda
     * @param esperaSegundos {@code long} silencio que cierra la tanda ({@code accesmed.chatwoot.agrupacion.espera-segundos})
     * @param esperaMaximaSegundos {@code long} tope desde el primer mensaje ({@code accesmed.chatwoot.agrupacion.espera-maxima-segundos})
     * @param maxMensajes {@code int} cantidad que cierra la tanda ({@code accesmed.chatwoot.agrupacion.max-mensajes})
     * @return {@code AgrupadorMensajes<ReceiveMensajeChatwootRequest>} agrupador por id de conversación
     */
    @Bean(destroyMethod = "close")
    public AgrupadorMensajes<ReceiveMensajeChatwootRequest> agrupadorMensajesChatwoot(
            @Qualifier("chatwootTaskExecutor") Executor chatwootTaskExecutor,
            @Value("${accesmed.chatwoot.agrupacion.espera-segundos:4}") long esperaSegundos,
            @Value("${accesmed.chatwoot.agrupacion.espera-maxima-segundos:15}") long esperaMaximaSegundos,
            @Value("${accesmed.chatwoot.agrupacion.max-mensajes:5}") int maxMensajes) {

        AgrupadorMensajes<ReceiveMensajeChatwootRequest> agrupadorMensajesChatwoot = new AgrupadorMensajes<>(
                Duration.ofSeconds(esperaSegundos), Duration.ofSeconds(esperaMaximaSegundos), maxMensajes, chatwootTaskExecutor);
        return agrupadorMensajesChatwoot;

    }

    //endregion

}
