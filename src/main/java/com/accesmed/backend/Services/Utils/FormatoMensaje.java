package com.accesmed.backend.Services.Utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatea valores (fechas, estados) para los mensajes de error que ve el usuario: fechas
 * en {@code dd/MM/yyyy}, sin zona horaria ni segundos, y estados en minúscula y con
 * espacios, en vez del {@code toString()} técnico de Java.
 */
public final class FormatoMensaje {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatoMensaje() {

    }

    /**
     * Formatea una fecha de calendario como {@code dd/MM/yyyy}.
     *
     * @param fecha {@code LocalDate} fecha a formatear, o {@code null}
     * @return {@code String} la fecha formateada, o {@code "una fecha desconocida"} si es {@code null}
     */
    public static String fecha(LocalDate fecha) {

        return fecha != null ? FORMATO_FECHA.format(fecha) : "una fecha desconocida";

    }

    /**
     * Formatea una fecha y hora como {@code dd/MM/yyyy HH:mm}.
     *
     * @param fechaHora {@code ZonedDateTime} fecha y hora a formatear, o {@code null}
     * @return {@code String} la fecha y hora formateadas, o {@code "una fecha desconocida"} si es {@code null}
     */
    public static String fechaHora(ZonedDateTime fechaHora) {

        return fechaHora != null ? FORMATO_FECHA_HORA.format(fechaHora) : "una fecha desconocida";

    }

    /**
     * Formatea una duración en minutos ({@code 30 minutos}), en vez del {@code PT30M} técnico de Java.
     *
     * @param duracion {@code Duration} duración a formatear
     * @return {@code String} la duración expresada en minutos
     */
    public static String duracion(Duration duracion) {

        long minutos = duracion.toMinutes();
        return minutos + (minutos == 1 ? " minuto" : " minutos");

    }

    /**
     * Convierte el nombre de un estado enum ({@code NO_PUBLICADA}) a texto legible
     * ({@code no publicada}).
     *
     * @param estado {@code Enum<?>} estado a formatear
     * @return {@code String} el nombre del estado en minúscula y con espacios
     */
    public static String estado(Enum<?> estado) {

        return estado.name().toLowerCase(Locale.ROOT).replace('_', ' ');

    }

}
