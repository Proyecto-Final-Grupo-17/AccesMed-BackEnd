package com.accesmed.backend.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el soporte de tareas programadas ({@code @Scheduled}) de Spring
 * para el paquete {@code Schedulers}. Los beans del paquete usan esta anotación
 * para ejecutar métodos automáticamente según un cron configurable.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
