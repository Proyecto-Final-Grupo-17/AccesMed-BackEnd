package com.accesmed.backend.Services.Utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Formatea valores (fechas, estados) para los mensajes de error que ve el usuario: fechas
 * en {@code dd/MM/yyyy}, sin zona horaria ni segundos, y estados en minúscula y con
 * espacios, en vez del {@code toString()} técnico de Java.
 */
public final class FormatoMensaje {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale LOCALE_ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FORMATO_FECHA_HORA_TEXTO =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy HH:mm", LOCALE_ES_AR);

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
     * Formatea una fecha y hora como {@code dd/MM/yyyy HH:mm} en la zona horaria de la clínica.
     * Los {@code ZonedDateTime} que devuelve la persistencia vienen en UTC: sin convertir, un
     * turno de las 14:00 en Argentina se mostraría como 17:00.
     *
     * @param fechaHora {@code ZonedDateTime} fecha y hora a formatear, o {@code null}
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica ({@code Clinica.zonaHoraria})
     * @return {@code String} la fecha y hora formateadas, o {@code "una fecha desconocida"} si es {@code null}
     */
    public static String fechaHora(ZonedDateTime fechaHora, ZoneId zonaHorariaClinica) {

        return fechaHora != null
                ? FORMATO_FECHA_HORA.format(fechaHora.withZoneSameInstant(zonaHorariaClinica))
                : "una fecha desconocida";

    }

    /**
     * Formatea una fecha y hora local de la clínica para el paciente, con el día de la semana
     * ({@code jueves 12/11/2026 09:30}). El agente la copia tal cual: si el LLM calcula el día de
     * la semana a partir de la fecha ISO, se equivoca (Diseno-Chatbot §4.6).
     *
     * @param fechaHora {@code LocalDateTime} fecha y hora ya expresadas en la zona de la clínica, o {@code null}
     * @return {@code String} la fecha y hora legibles, o {@code null} si la fecha es {@code null}
     */
    public static String fechaHoraTexto(LocalDateTime fechaHora) {

        String fechaHoraTexto = fechaHora != null ? FORMATO_FECHA_HORA_TEXTO.format(fechaHora) : null;
        return fechaHoraTexto;

    }

    /**
     * Devuelve el día de la semana de una fecha en español ({@code jueves}).
     *
     * @param fecha {@code LocalDate} fecha de calendario
     * @return {@code String} el día de la semana en minúscula
     */
    public static String diaSemana(LocalDate fecha) {

        String diaSemana = fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_ES_AR).toLowerCase(LOCALE_ES_AR);
        return diaSemana;

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
