package com.accesmed.backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor del pasamanos de Chatwoot: el webhook responde 200 de inmediato y la consulta a
 * Flowise (que puede tardar decenas de segundos) corre en estos hilos, para que Chatwoot no
 * reintente ni dé timeout.
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

    //endregion

}
