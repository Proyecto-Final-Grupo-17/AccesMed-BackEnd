package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Services.Errors.ValidacionException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculo puro de expansión de bloques horarios a slots reservables ({@code AgendaHorarios}).
 * Sin repositorio: la comparten {@code createAgendaMedico} y {@code updateAgendaMedico}
 * (vía {@code horariosAAgregar}), y no le pertenece a ninguna de las tres entidades de
 * Agenda, así que vive en {@code Services/Utils} (ver {@code Docs/ARQUITECTURA.md §4}).
 *
 * <p>Valida, por cada bloque: que {@code horaDesde < horaHasta} dentro del horario de
 * atención de la clínica, que {@code duracionTurno} divida exactamente al bloque, que la
 * duración del slot caiga entre {@code duracionMinima} y {@code duracionMaxima} de la
 * prestación, y que no se superponga con ningún otro bloque del mismo día — ni con los que
 * ya vinieron en el lote, ni con los que ya están en base ({@code rangosExistentesPorDia}).
 * Todos los errores de forma se acumulan y se lanzan juntos en un único
 * {@link ValidacionException}.</p>
 */
@Component
public class GeneradorSlotsAgenda {

    //region ========== Tipos auxiliares ==========

    /**
     * Bloque horario a expandir en slots, para un día y una prestación concretos.
     *
     * @param fecha {@code LocalDate} día del bloque
     * @param horaDesde {@code LocalTime} inicio del bloque
     * @param horaHasta {@code LocalTime} fin del bloque
     * @param prestacion {@code Prestacion} prestación del bloque (define duración mínima/máxima y tolerancia)
     * @param duracionTurno {@code Duration} duración de cada slot dentro del bloque
     */
    public record BloqueAGenerar(LocalDate fecha, LocalTime horaDesde, LocalTime horaHasta,
            Prestacion prestacion, Duration duracionTurno) {

    }

    /**
     * Rango horario ya ocupado dentro de un día, usado para detectar superposición.
     *
     * @param horaDesde {@code LocalTime} inicio del rango existente
     * @param horaHasta {@code LocalTime} fin del rango existente
     */
    public record RangoHorario(LocalTime horaDesde, LocalTime horaHasta) {

    }

    /**
     * Slot generado, listo para mapear a una entidad {@code AgendaHorarios}.
     *
     * @param fecha {@code LocalDate} día del slot
     * @param horaDesde {@code LocalTime} inicio del slot
     * @param horaHasta {@code LocalTime} fin del slot
     * @param prestacion {@code Prestacion} prestación del slot
     * @param fechaLimiteReserva {@code ZonedDateTime} inicio del slot menos la tolerancia de solicitud
     */
    public record SlotGenerado(LocalDate fecha, LocalTime horaDesde, LocalTime horaHasta,
            Prestacion prestacion, ZonedDateTime fechaLimiteReserva) {

    }

    //endregion

    //region ========== Métodos ==========

    /**
     * Expande una lista de bloques a sus slots, validando la forma de cada uno y
     * acumulando los errores de todo el lote en un único {@link ValidacionException}.
     *
     * @param bloques {@code List<BloqueAGenerar>} bloques a expandir
     * @param horarioInicioAtencion {@code LocalTime} inicio del horario de atención de la clínica
     * @param horarioFinAtencion {@code LocalTime} fin del horario de atención de la clínica
     * @param rangosExistentesPorDia {@code Map<LocalDate, List<RangoHorario>>} rangos ya ocupados
     *        en base por día, contra los que también se valida la superposición
     * @param zonaHoraria {@code ZoneId} zona horaria para calcular {@code fechaLimiteReserva}
     * @return {@code List<SlotGenerado>} todos los slots generados por el lote completo
     * @throws ValidacionException {@code ValidacionException} con la lista completa de errores de
     *         forma encontrados en el lote, si hubo alguno
     */
    public List<SlotGenerado> generar(List<BloqueAGenerar> bloques, LocalTime horarioInicioAtencion,
            LocalTime horarioFinAtencion, Map<LocalDate, List<RangoHorario>> rangosExistentesPorDia,
            ZoneId zonaHoraria) {

        List<String> errores = new ArrayList<>();
        List<SlotGenerado> slotsGenerados = new ArrayList<>();

        Map<LocalDate, List<RangoHorario>> rangosAcumulados = new HashMap<>();
        rangosExistentesPorDia.forEach((fecha, rangos) -> rangosAcumulados.put(fecha, new ArrayList<>(rangos)));

        for (BloqueAGenerar bloque : bloques) {

            String descripcionBloque = bloque.fecha() + " " + bloque.horaDesde() + "-" + bloque.horaHasta();

            if (!bloque.horaDesde().isBefore(bloque.horaHasta())) {
                errores.add("Bloque " + descripcionBloque + ": la hora desde debe ser anterior a la hora hasta.");
                continue;
            }

            if (bloque.horaDesde().isBefore(horarioInicioAtencion) || bloque.horaHasta().isAfter(horarioFinAtencion)) {
                errores.add("Bloque " + descripcionBloque + ": debe caer dentro del horario de atención de la clínica ("
                        + horarioInicioAtencion + "-" + horarioFinAtencion + ").");
                continue;
            }

            long segundosBloque = Duration.between(bloque.horaDesde(), bloque.horaHasta()).getSeconds();
            long segundosTurno = bloque.duracionTurno().getSeconds();

            if (segundosTurno <= 0 || segundosBloque % segundosTurno != 0) {
                errores.add("Bloque " + descripcionBloque + ": la duración del turno (" + bloque.duracionTurno()
                        + ") no divide exactamente al bloque.");
                continue;
            }

            if (segundosTurno < bloque.prestacion().getDuracionMinima().getSeconds()
                    || segundosTurno > bloque.prestacion().getDuracionMaxima().getSeconds()) {
                errores.add("Bloque " + descripcionBloque + ": la duración del turno (" + bloque.duracionTurno()
                        + ") no está entre la duración mínima y máxima de la prestación " + bloque.prestacion().getId() + ".");
                continue;
            }

            List<RangoHorario> rangosDelDia = rangosAcumulados.computeIfAbsent(bloque.fecha(), fecha -> new ArrayList<>());
            boolean seSuperpone = rangosDelDia.stream().anyMatch(rango ->
                    bloque.horaDesde().isBefore(rango.horaHasta()) && rango.horaDesde().isBefore(bloque.horaHasta()));

            if (seSuperpone) {
                errores.add("Bloque " + descripcionBloque + ": se superpone con otro bloque del mismo día.");
                continue;
            }

            rangosDelDia.add(new RangoHorario(bloque.horaDesde(), bloque.horaHasta()));

            long cantidadSlots = segundosBloque / segundosTurno;
            for (long n = 0; n < cantidadSlots; n++) {

                LocalTime inicioSlot = bloque.horaDesde().plusSeconds(n * segundosTurno);
                LocalTime finSlot = inicioSlot.plusSeconds(segundosTurno);
                ZonedDateTime inicioSlotZoneado = ZonedDateTime.of(bloque.fecha(), inicioSlot, zonaHoraria);
                ZonedDateTime fechaLimiteReserva = inicioSlotZoneado.minus(bloque.prestacion().getTiempoToleranciaSolicitud());

                slotsGenerados.add(new SlotGenerado(bloque.fecha(), inicioSlot, finSlot, bloque.prestacion(), fechaLimiteReserva));

            }

        }

        if (!errores.isEmpty()) {
            throw new ValidacionException(getClass(), errores);
        }

        return slotsGenerados;

    }

    //endregion

}
