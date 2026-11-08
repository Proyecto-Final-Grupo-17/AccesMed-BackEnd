package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Services.Utils.NormalizadorTelefono;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estado de la tanda en curso del canal chatbot, por teléfono del paciente (diseño en
 * {@code Docs/Diseno-Chatbot.md §3.4.5} y {@code §3.4.6}). {@code ChatwootApp} abre la tanda al
 * consultar a Flowise y la cierra al responder; mientras tanto {@code AgenteApp} registra las tools
 * que el agente llamó, cuáles terminaron bien y los datos que devolvieron.
 * <p>
 * Con eso se resuelven dos cosas: el <b>aviso de espera</b> (uno por tanda, al primer pedido que lo
 * amerita) y la <b>verificación de la respuesta</b>: el agente no puede anunciar una escritura que no
 * se ejecutó en la tanda, ni mostrar códigos de turno, matrículas u horarios que ninguna tool
 * devolvió en la sesión de Flowise. Los datos devueltos se acumulan por sesión (no por tanda) para
 * que el agente pueda repetir en un resumen lo que mostró en el mensaje anterior.
 * <p>
 * Vive en memoria: supone una sola instancia del back, igual que el resto del estado del canal.
 */
@Slf4j
@Service
public class TandaAgenteService {

    //region ========== Constantes ==========

    /**
     * Tiempo sin uso tras el cual se borra el estado de un teléfono.
     */
    private static final Duration VENCIMIENTO_ESTADO = Duration.ofHours(2);

    private static final Pattern PATRON_CODIGO_TURNO = Pattern.compile("TURNO_[0-9A-F]{8}");

    private static final Pattern PATRON_MATRICULA_ROTULADA =
            Pattern.compile("(?i)matr[ií]cula\\s*:?\\s*\\*?\\s*([A-Za-z0-9][A-Za-z0-9-]*)");

    private static final Pattern PATRON_MATRICULA_MP = Pattern.compile("\\bMP-[A-Za-z0-9]+\\b");

    /**
     * Fecha ({@code dd/MM} o {@code dd/MM/aaaa}) seguida, en la misma línea, de una hora ({@code HH:mm}).
     */
    private static final Pattern PATRON_FECHA_HORA =
            Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/\\d{2,4})?[^\\d\\n]{0,25}?(\\d{1,2})[:.](\\d{2})\\b");

    private static final Pattern PATRON_NEGACION = Pattern.compile("\\bno\\b");

    /**
     * Frases que anuncian una escritura ya hecha, por acción (sobre texto en minúsculas y sin tildes).
     */
    private static final Map<AccionAgente, Pattern> PATRONES_ESCRITURA = Map.of(
            AccionAgente.RESERVA_TURNO, buildPatronEscritura("reserve|agende", "reservad[oa]s?|agendad[oa]s?", "reservo|agendo"),
            AccionAgente.CANCELACION_TURNO, buildPatronEscritura("cancele", "cancelad[oa]s?", "cancelo"),
            AccionAgente.REPROGRAMACION_TURNO, buildPatronEscritura("reprograme", "reprogramad[oa]s?", "reprogramo"),
            AccionAgente.CONFIRMACION_TURNO, buildPatronEscritura("confirme", "confirmad[oa]s?", "confirmo"),
            AccionAgente.REGISTRO_PACIENTE, buildPatronEscritura("registre", "registrad[oa]s?", "registro"),
            AccionAgente.MODIFICACION_PACIENTE, buildPatronEscritura("actualice|modifique", "actualizad[oa]s?|modificad[oa]s?", "actualizo"),
            AccionAgente.BAJA_PACIENTE, Pattern.compile("\\b(di|dimos) de baja\\b|\\bdad[oa] de baja\\b"));

    //endregion

    //region ========== Atributos ==========

    /**
     * Estado por teléfono (solo dígitos).
     */
    private final Map<String, EstadoTanda> estadosPorTelefono = new ConcurrentHashMap<>();

    //endregion

    //region ========== Métodos: ciclo de la tanda ==========

    /**
     * Abre la tanda de un teléfono antes de consultar a Flowise. Si la sesión de Flowise cambió,
     * olvida los datos de la sesión anterior.
     *
     * @param numeroTelefono {@code String} teléfono del paciente, en cualquier formato
     * @param conversationId {@code long} conversación de Chatwoot (destino del aviso de espera)
     * @param sessionIdFlowise {@code String} sesión de Flowise de la ventana vigente
     */
    public void openTanda(String numeroTelefono, long conversationId, String sessionIdFlowise) {

        EstadoTanda estadoTanda = estadosPorTelefono.computeIfAbsent(buildClave(numeroTelefono), clave -> new EstadoTanda());

        synchronized (estadoTanda) {
            if (!sessionIdFlowise.equals(estadoTanda.sessionIdFlowise)) {
                estadoTanda.sessionIdFlowise = sessionIdFlowise;
                estadoTanda.codigosConocidos.clear();
                estadoTanda.matriculasConocidas.clear();
                estadoTanda.fechasHorasConocidas.clear();
            }
            estadoTanda.conversationId = conversationId;
            estadoTanda.abierta = true;
            estadoTanda.avisoEsperaEnviado = false;
            estadoTanda.accionesExitosas.clear();
            estadoTanda.ultimoUso = Instant.now();
        }

    }

    /**
     * Cierra la tanda de un teléfono: las llamadas que lleguen después ya no avisan. Los datos de la
     * sesión se conservan para la tanda siguiente.
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

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
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
     * Registra que una acción terminó bien en la tanda en curso.
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @param accionAgente {@code AccionAgente} acción ejecutada
     */
    public void registerAccionExitosa(String numeroTelefono, AccionAgente accionAgente) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null) {
            return;
        }

        synchronized (estadoTanda) {
            if (estadoTanda.abierta) {
                estadoTanda.accionesExitosas.add(accionAgente);
            }
        }

    }

    /**
     * Registra los códigos de turno que devolvió una tool (válidos para toda la sesión).
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @param codigosTurno {@code Collection<String>} códigos devueltos
     */
    public void registerCodigosTurno(String numeroTelefono, Collection<String> codigosTurno) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null) {
            return;
        }

        synchronized (estadoTanda) {
            estadoTanda.codigosConocidos.addAll(codigosTurno);
        }

    }

    /**
     * Registra las matrículas que devolvió una tool (válidas para toda la sesión).
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @param matriculas {@code Collection<String>} matrículas devueltas
     */
    public void registerMatriculas(String numeroTelefono, Collection<String> matriculas) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null) {
            return;
        }

        synchronized (estadoTanda) {
            matriculas.forEach(matricula -> estadoTanda.matriculasConocidas.add(matricula.toUpperCase(Locale.ROOT)));
        }

    }

    /**
     * Registra las fechas y horas que devolvió una tool: horarios disponibles e inicios de turnos
     * (válidas para toda la sesión).
     *
     * @param numeroTelefono {@code String} teléfono del paciente (header del canal), o {@code null}
     * @param fechasHoras {@code Collection<LocalDateTime>} fechas y horas en la zona de la clínica
     */
    public void registerFechasHoras(String numeroTelefono, Collection<LocalDateTime> fechasHoras) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null) {
            return;
        }

        synchronized (estadoTanda) {
            fechasHoras.forEach(fechaHora -> estadoTanda.fechasHorasConocidas.add(
                    buildClaveFechaHora(fechaHora.getDayOfMonth(), fechaHora.getMonthValue(),
                            fechaHora.getHour(), fechaHora.getMinute())));
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

    //region ========== Métodos: verificación ==========

    /**
     * Verifica la respuesta del agente contra lo que las tools hicieron y devolvieron:
     * <ul>
     *   <li>una escritura anunciada como hecha tiene que haber terminado bien en esta tanda;</li>
     *   <li>cada código de turno, matrícula y fecha con hora que muestra tiene que haberlo devuelto
     *       alguna tool en la sesión.</li>
     * </ul>
     *
     * @param numeroTelefono {@code String} teléfono del paciente
     * @param respuestaAgente {@code String} texto que devolvió Flowise
     * @return {@code Optional<String>} la instrucción de corrección para el agente si no pasa, o vacío si pasa
     */
    public Optional<String> verifyRespuestaAgente(String numeroTelefono, String respuestaAgente) {

        EstadoTanda estadoTanda = findEstadoTanda(numeroTelefono);
        if (estadoTanda == null || respuestaAgente == null || respuestaAgente.isBlank()) {
            return Optional.empty();
        }

        List<String> problemas = new ArrayList<>();
        synchronized (estadoTanda) {

            //Escrituras anunciadas que no se ejecutaron en esta tanda
            for (AccionAgente accionAnunciada : findEscriturasAnunciadas(respuestaAgente)) {
                boolean ejecutada = estadoTanda.accionesExitosas.contains(accionAnunciada)
                        || (accionAnunciada == AccionAgente.RESERVA_TURNO
                                && estadoTanda.accionesExitosas.contains(AccionAgente.REPROGRAMACION_TURNO));
                if (!ejecutada) {
                    problemas.add("Le dijiste al paciente que la acción ya está hecha, pero en este mensaje no se ejecutó "
                            + accionAnunciada.getHerramienta() + " con éxito. Si el paciente ya confirmó, llamá a esa "
                            + "herramienta ahora; si no, no digas que se hizo.");
                }
            }

            //Códigos de turno que ninguna tool devolvió
            Set<String> codigosInventados = findCoincidencias(PATRON_CODIGO_TURNO, respuestaAgente, 0);
            codigosInventados.removeAll(estadoTanda.codigosConocidos);
            if (!codigosInventados.isEmpty()) {
                problemas.add("Mencionaste los turnos " + codigosInventados + ", que ninguna herramienta devolvió. "
                        + "Consultá con listTurnos y usá solo los códigos que figuren ahí.");
            }

            //Matrículas que ninguna tool devolvió
            Set<String> matriculasMencionadas = findCoincidencias(PATRON_MATRICULA_ROTULADA, respuestaAgente, 1);
            matriculasMencionadas.addAll(findCoincidencias(PATRON_MATRICULA_MP, respuestaAgente, 0));
            Set<String> matriculasInventadas = new LinkedHashSet<>();
            matriculasMencionadas.forEach(matricula -> {
                if (!estadoTanda.matriculasConocidas.contains(matricula.toUpperCase(Locale.ROOT))) {
                    matriculasInventadas.add(matricula);
                }
            });
            if (!matriculasInventadas.isEmpty()) {
                problemas.add("Mencionaste las matrículas " + matriculasInventadas + ", que ninguna herramienta devolvió. "
                        + "Consultá con getOfertaEspecialidad, searchMedico o searchPrestacion y mostrá solo médicos que figuren ahí.");
            }

            //Fechas con hora que no salieron de una tool (horarios o turnos)
            Set<String> fechasHorasInventadas = findFechasHoras(respuestaAgente);
            fechasHorasInventadas.removeAll(estadoTanda.fechasHorasConocidas);
            if (!fechasHorasInventadas.isEmpty()) {
                problemas.add("Mostraste los horarios " + fechasHorasInventadas + ", que no devolvió ninguna herramienta. "
                        + "Consultá con listHorariosDisponibles (o listTurnos) y mostrá solo lo que devuelva.");
            }

        }

        if (problemas.isEmpty()) {
            return Optional.empty();
        }

        log.warn("Respuesta del agente no verificada: {}", problemas);
        String instruccionCorreccion = String.join(" ", problemas);
        return Optional.of(instruccionCorreccion);

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Arma el patrón que detecta el anuncio de una escritura hecha: primera persona del pasado
     * ("te reservé"), participio con un verbo que afirma el resultado ("quedó reservado", "ya estás
     * registrado") o presente después de "listo" ("listo, te lo confirmo"). Un participio suelto
     * ("estado: CONFIRMADO" en un listado) no cuenta.
     *
     * @param pasado {@code String} alternativas del pretérito en primera persona, sin tildes
     * @param participio {@code String} alternativas del participio
     * @param presente {@code String} alternativas del presente en primera persona
     * @return {@code Pattern} patrón sobre texto en minúsculas y sin tildes
     */
    private static Pattern buildPatronEscritura(String pasado, String participio, String presente) {

        String regex = "\\b(" + pasado + ")\\b"
                + "|\\b(quedo|quedaron|quedaste|ya esta|ya estas|ya estan)\\b[^.\\n]{0,30}?\\b(" + participio + ")\\b"
                + "|\\blisto\\b[^.\\n]{0,20}?\\b(te )?(lo |la )?(" + presente + ")\\b";
        return Pattern.compile(regex);

    }

    /**
     * Busca las escrituras que el texto anuncia como hechas. Ignora las oraciones que preguntan
     * (llevan {@code ?}) o niegan ("no pude reservarlo").
     *
     * @param respuestaAgente {@code String} texto del agente
     * @return {@code Set<AccionAgente>} acciones anunciadas
     */
    private Set<AccionAgente> findEscriturasAnunciadas(String respuestaAgente) {

        Set<AccionAgente> escriturasAnunciadas = EnumSet.noneOf(AccionAgente.class);

        for (String oracion : respuestaAgente.split("(?<=[.!?\\n])")) {
            if (oracion.contains("?") || oracion.contains("¿")) {
                continue;
            }
            String oracionNormalizada = normalizarTexto(oracion);
            if (PATRON_NEGACION.matcher(oracionNormalizada).find()) {
                continue;
            }
            PATRONES_ESCRITURA.forEach((accionAgente, patron) -> {
                if (patron.matcher(oracionNormalizada).find()) {
                    escriturasAnunciadas.add(accionAgente);
                }
            });
        }

        return escriturasAnunciadas;

    }

    /**
     * Junta las coincidencias de un patrón en el texto.
     *
     * @param patron {@code Pattern} patrón a buscar
     * @param texto {@code String} texto donde buscar
     * @param grupo {@code int} grupo del patrón a devolver (0 = coincidencia completa)
     * @return {@code Set<String>} coincidencias, en orden de aparición
     */
    private Set<String> findCoincidencias(Pattern patron, String texto, int grupo) {

        Set<String> coincidencias = new LinkedHashSet<>();
        Matcher matcher = patron.matcher(texto);
        while (matcher.find()) {
            coincidencias.add(matcher.group(grupo));
        }

        return coincidencias;

    }

    /**
     * Junta las fechas con hora que muestra el texto, como claves {@code dd/MM HH:mm}.
     *
     * @param texto {@code String} texto del agente
     * @return {@code Set<String>} fechas con hora encontradas
     */
    private Set<String> findFechasHoras(String texto) {

        Set<String> fechasHoras = new LinkedHashSet<>();
        Matcher matcher = PATRON_FECHA_HORA.matcher(texto);
        while (matcher.find()) {
            fechasHoras.add(buildClaveFechaHora(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))));
        }

        return fechasHoras;

    }

    /**
     * Arma la clave de comparación de una fecha con hora (sin año: el agente no siempre lo escribe).
     *
     * @param dia {@code int} día del mes
     * @param mes {@code int} mes
     * @param hora {@code int} hora
     * @param minuto {@code int} minuto
     * @return {@code String} clave {@code dd/MM HH:mm}
     */
    private static String buildClaveFechaHora(int dia, int mes, int hora, int minuto) {

        String claveFechaHora = String.format("%02d/%02d %02d:%02d", dia, mes, hora, minuto);
        return claveFechaHora;

    }

    /**
     * Pasa el texto a minúsculas y sin tildes, conservando la puntuación.
     *
     * @param texto {@code String} texto a normalizar
     * @return {@code String} el texto normalizado
     */
    private String normalizarTexto(String texto) {

        String textoNormalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return textoNormalizado;

    }

    /**
     * Busca el estado del teléfono, si tiene una tanda abierta o una sesión en curso.
     *
     * @param numeroTelefono {@code String} teléfono del paciente, o {@code null}
     * @return {@code EstadoTanda} el estado, o {@code null} si no hay
     */
    private EstadoTanda findEstadoTanda(String numeroTelefono) {

        if (numeroTelefono == null || numeroTelefono.isBlank()) {
            return null;
        }

        EstadoTanda estadoTanda = estadosPorTelefono.get(buildClave(numeroTelefono));
        return estadoTanda;

    }

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

        private String sessionIdFlowise;
        private long conversationId;
        private boolean abierta;
        private boolean avisoEsperaEnviado;
        private Instant ultimoUso = Instant.now();
        private final Set<AccionAgente> accionesExitosas = EnumSet.noneOf(AccionAgente.class);
        private final Set<String> codigosConocidos = new HashSet<>();
        private final Set<String> matriculasConocidas = new HashSet<>();
        private final Set<String> fechasHorasConocidas = new HashSet<>();

    }

}
