package com.accesmed.backend.Services.ExternalServices;

import com.accesmed.backend.Services.Clients.OpenAiClient;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lógica del audio del canal (diseño en {@code Docs/Diseno-Chatbot.md §5}): decide si el audio está
 * habilitado, valida y transcribe los audios del paciente, y arma el texto para voz de la respuesta
 * (determinista: sin formato ni emojis, con fechas y montos legibles) antes de sintetizarla. El
 * audio queda apagado si no hay API key de OpenAI. También sirve las notas de voz pregrabadas
 * ({@link AudioFijo}), que se leen de disco una sola vez al arrancar.
 */
@Slf4j
@Service
public class OpenAiService {

    //region ========== Constantes ==========

    /**
     * Código de negocio cuando el audio supera {@code accesmed.audio.max-bytes}.
     */
    public static final String CODIGO_AUDIO_DEMASIADO_LARGO = "AUDIO_DEMASIADO_LARGO";

    /**
     * Código de negocio cuando la transcripción vuelve vacía.
     */
    public static final String CODIGO_AUDIO_NO_ENTENDIDO = "AUDIO_NO_ENTENDIDO";

    /**
     * Tope de caracteres del texto de entrada de la API de voz.
     */
    static final int MAX_CARACTERES_VOZ = 4096;

    private static final Locale LOCALE_ES_AR = Locale.forLanguageTag("es-AR");

    private static final Pattern PATRON_FECHA_HORA =
            Pattern.compile("(\\p{L}+) (\\d{2})/(\\d{2})/(\\d{4}) (\\d{1,2}):(\\d{2})");

    private static final Pattern PATRON_FECHA = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    private static final Pattern PATRON_MONTO = Pattern.compile("\\$\\s?(\\d[\\d.]*(?:,\\d+)?)");

    private static final Pattern PATRON_CODIGO_TURNO = Pattern.compile("TURNO_[A-Z0-9]+");

    private static final Pattern PATRON_VINETA = Pattern.compile("(?m)^[ \\t]*[-•·*]+[ \\t]+");

    private static final Pattern PATRON_FORMATO = Pattern.compile("[*_~#]");

    private static final Pattern PATRON_EMOJI = Pattern.compile("[\\p{So}\\p{Sk}\\uFE0F\\u200D]");

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final OpenAiClient openAiClient;
    private final boolean audioHabilitado;
    private final long maxBytes;
    private final Path carpetaAudiosFijos;

    /**
     * Audios fijos cargados en memoria al arrancar (los que faltan no figuran).
     */
    private final Map<AudioFijo, byte[]> audiosFijos = new EnumMap<>(AudioFijo.class);

    //endregion

    /**
     * Construye el servicio del audio del canal.
     *
     * @param openAiClient {@code OpenAiClient} cliente de la API de OpenAI
     * @param apiKey {@code String} API key de OpenAI ({@code accesmed.openai.api-key}); vacía = audio apagado
     * @param maxBytes {@code long} tamaño máximo de un audio a transcribir ({@code accesmed.audio.max-bytes})
     * @param fijosDir {@code String} carpeta de los audios fijos ({@code accesmed.audio.fijos-dir})
     */
    public OpenAiService(OpenAiClient openAiClient,
            @Value("${accesmed.openai.api-key:}") String apiKey,
            @Value("${accesmed.audio.max-bytes:10485760}") long maxBytes,
            @Value("${accesmed.audio.fijos-dir:./audios}") String fijosDir) {

        this.openAiClient = openAiClient;
        this.audioHabilitado = apiKey != null && !apiKey.isBlank();
        this.maxBytes = maxBytes;
        this.carpetaAudiosFijos = Path.of(fijosDir);

    }

    /**
     * Carga en memoria los audios fijos de la carpeta {@code accesmed.audio.fijos-dir}. Si falta la
     * carpeta o un archivo, lo avisa y sigue: ese caso se manda en texto (nunca falla el arranque).
     * Con el audio apagado (sin API key) no se cargan: nunca se usarían.
     */
    @PostConstruct
    void loadAudiosFijos() {

        if (!audioHabilitado) {
            log.debug("Audio apagado: no se cargan los audios fijos");
            return;
        }
        if (!Files.isDirectory(carpetaAudiosFijos)) {
            log.warn("No existe la carpeta de audios fijos {}: esos mensajes saldrán solo en texto",
                    carpetaAudiosFijos.toAbsolutePath());
            return;
        }

        for (AudioFijo audioFijo : AudioFijo.values()) {
            Path archivo = carpetaAudiosFijos.resolve(audioFijo.nombreArchivo());
            try {
                byte[] contenido = Files.readAllBytes(archivo);
                if (contenido.length == 0) {
                    log.warn("El audio fijo {} está vacío: ese mensaje saldrá en texto", archivo.toAbsolutePath());
                    continue;
                }
                audiosFijos.put(audioFijo, contenido);
            } catch (IOException excepcion) {
                log.warn("No se pudo leer el audio fijo {}: ese mensaje saldrá en texto ({})",
                        archivo.toAbsolutePath(), excepcion.getMessage());
            }
        }
        log.info("Audios fijos cargados: {} de {} desde {}", audiosFijos.size(), AudioFijo.values().length,
                carpetaAudiosFijos.toAbsolutePath());

    }

    //region ========== Métodos ==========

    /**
     * Indica si el audio está habilitado (hay API key de OpenAI).
     *
     * @return {@code boolean} {@code true} si se pueden transcribir audios y sintetizar voz
     */
    public boolean isAudioHabilitado() {

        return audioHabilitado;

    }

    /**
     * Obtiene el contenido de un audio fijo.
     *
     * @param audioFijo {@code AudioFijo} audio pregrabado que se quiere mandar
     * @return {@code Optional<byte[]>} el audio OGG/Opus, o vacío si el archivo no se cargó
     */
    public Optional<byte[]> findAudioFijo(AudioFijo audioFijo) {

        Optional<byte[]> audioFijoCargado = Optional.ofNullable(audiosFijos.get(audioFijo));
        return audioFijoCargado;

    }

    /**
     * Indica si un audio pesa más que el máximo permitido.
     *
     * @param bytes {@code long} tamaño del audio en bytes
     * @return {@code boolean} {@code true} si supera {@code accesmed.audio.max-bytes}
     */
    public boolean isAudioDemasiadoLargo(long bytes) {

        boolean demasiadoLargo = bytes > maxBytes;
        return demasiadoLargo;

    }

    /**
     * Transcribe el audio de un mensaje del paciente.
     *
     * @param audio {@code byte[]} contenido del audio
     * @param nombreArchivo {@code String} nombre del archivo (define el formato para OpenAI)
     * @param mime {@code String} tipo MIME del audio
     * @return {@code String} el texto transcripto, sin espacios sobrantes
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el audio supera el máximo o la transcripción vuelve vacía
     */
    public String transcribeAudioMensaje(byte[] audio, String nombreArchivo, String mime) {

        if (isAudioDemasiadoLargo(audio.length)) {
            log.warn("Audio descartado por tamaño: bytes={}, máximo={}", audio.length, maxBytes);
            throw new ReglaNegocioException(getClass(), CODIGO_AUDIO_DEMASIADO_LARGO,
                    "El audio supera el tamaño máximo permitido.");
        }

        String textoTranscripto = openAiClient.transcribeAudio(audio, nombreArchivo, mime);
        if (textoTranscripto == null || textoTranscripto.isBlank()) {
            log.warn("OpenAI devolvió una transcripción vacía para un audio de {} bytes", audio.length);
            throw new ReglaNegocioException(getClass(), CODIGO_AUDIO_NO_ENTENDIDO,
                    "No se pudo transcribir el audio.");
        }
        String textoTranscriptoLimpio = textoTranscripto.trim();
        return textoTranscriptoLimpio;

    }

    /**
     * Sintetiza la respuesta en una nota de voz. Arma antes el texto para voz; si queda vacío o supera
     * el tope de la API ({@value #MAX_CARACTERES_VOZ} caracteres), no hay voz y la respuesta va solo en texto.
     *
     * @param texto {@code String} respuesta ya sin tokens
     * @param textoAparte {@code boolean} {@code true} si el texto escrito también se manda (los códigos de turno no se leen)
     * @return {@code Optional<byte[]>} el audio OGG/Opus, o vacío si no corresponde mandar voz
     * @throws org.springframework.web.client.RestClientException si OpenAI no responde o devuelve error
     */
    public Optional<byte[]> createVozRespuesta(String texto, boolean textoAparte) {

        String textoParaVoz = buildTextoParaVoz(texto, textoAparte);
        if (textoParaVoz.isEmpty()) {
            return Optional.empty();
        }
        if (textoParaVoz.length() > MAX_CARACTERES_VOZ) {
            log.info("La respuesta supera {} caracteres para voz ({}): se manda solo texto",
                    MAX_CARACTERES_VOZ, textoParaVoz.length());
            return Optional.empty();
        }

        byte[] audioVoz = openAiClient.createVoz(textoParaVoz);
        Optional<byte[]> audioRespuesta = audioVoz == null || audioVoz.length == 0
                ? Optional.empty() : Optional.of(audioVoz);
        return audioRespuesta;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Arma el texto para voz: sin formato ni emojis, con las fechas, horas y montos como se dicen.
     *
     * @param texto {@code String} respuesta ya sin tokens
     * @param textoAparte {@code boolean} si es {@code true}, los códigos {@code TURNO_…} no se leen
     * @return {@code String} el texto a sintetizar (vacío si no queda nada para leer)
     */
    String buildTextoParaVoz(String texto, boolean textoAparte) {

        String textoParaVoz = texto == null ? "" : texto;

        if (textoAparte) {
            textoParaVoz = PATRON_CODIGO_TURNO.matcher(textoParaVoz).replaceAll("(te lo dejo por escrito)");
        }

        textoParaVoz = replaceFechasHora(textoParaVoz);
        textoParaVoz = replaceFechas(textoParaVoz);
        textoParaVoz = PATRON_MONTO.matcher(textoParaVoz).replaceAll("$1 pesos");

        textoParaVoz = PATRON_VINETA.matcher(textoParaVoz).replaceAll("");
        textoParaVoz = PATRON_FORMATO.matcher(textoParaVoz).replaceAll(" ");
        textoParaVoz = PATRON_EMOJI.matcher(textoParaVoz).replaceAll("");
        textoParaVoz = textoParaVoz.replaceAll("[ \\t]{2,}", " ").replaceAll(" ?\\n ?", "\n").trim();
        return textoParaVoz;

    }

    /**
     * Reescribe {@code jueves 12/11/2026 09:30} como {@code jueves 12 de noviembre a las 9:30}.
     *
     * @param texto {@code String} texto de origen
     * @return {@code String} el texto con las fechas y horas legibles
     */
    private String replaceFechasHora(String texto) {

        Matcher coincidencia = PATRON_FECHA_HORA.matcher(texto);
        StringBuilder textoReemplazado = new StringBuilder();
        while (coincidencia.find()) {
            String fechaHoraLegible = coincidencia.group(1) + " " + Integer.parseInt(coincidencia.group(2)) + " de "
                    + nombreMes(coincidencia.group(3)) + " a las " + Integer.parseInt(coincidencia.group(5))
                    + ":" + coincidencia.group(6);
            coincidencia.appendReplacement(textoReemplazado, Matcher.quoteReplacement(fechaHoraLegible));
        }
        coincidencia.appendTail(textoReemplazado);
        return textoReemplazado.toString();

    }

    /**
     * Reescribe {@code 12/11/2026} como {@code 12 de noviembre de 2026}.
     *
     * @param texto {@code String} texto de origen
     * @return {@code String} el texto con las fechas legibles
     */
    private String replaceFechas(String texto) {

        Matcher coincidencia = PATRON_FECHA.matcher(texto);
        StringBuilder textoReemplazado = new StringBuilder();
        while (coincidencia.find()) {
            String fechaLegible = Integer.parseInt(coincidencia.group(1)) + " de "
                    + nombreMes(coincidencia.group(2)) + " de " + coincidencia.group(3);
            coincidencia.appendReplacement(textoReemplazado, Matcher.quoteReplacement(fechaLegible));
        }
        coincidencia.appendTail(textoReemplazado);
        return textoReemplazado.toString();

    }

    /**
     * Obtiene el nombre en español de un mes.
     *
     * @param numeroMes {@code String} mes de 1 a 12 (con o sin cero a la izquierda)
     * @return {@code String} el nombre del mes en minúsculas, o el número si no es válido
     */
    private String nombreMes(String numeroMes) {

        int mes = Integer.parseInt(numeroMes);
        String nombreMes = mes >= 1 && mes <= 12
                ? Month.of(mes).getDisplayName(TextStyle.FULL, LOCALE_ES_AR).toLowerCase(LOCALE_ES_AR)
                : numeroMes;
        return nombreMes;

    }

    //endregion

}
