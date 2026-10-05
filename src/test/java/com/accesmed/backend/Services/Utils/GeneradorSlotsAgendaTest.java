package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Services.Errors.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios de {@link GeneradorSlotsAgenda}: es donde se concentra la lógica de
 * expansión de bloques a slots (divisibilidad, bordes del horario de clínica,
 * superposición y cálculo de {@code fechaLimiteReserva}).
 */
class GeneradorSlotsAgendaTest {

    private static final LocalTime HORARIO_INICIO_ATENCION = LocalTime.of(8, 0);
    private static final LocalTime HORARIO_FIN_ATENCION = LocalTime.of(18, 0);
    private static final ZoneId ZONA_HORARIA = ZoneId.systemDefault();

    private GeneradorSlotsAgenda generadorSlotsAgenda;
    private Prestacion prestacion;

    @BeforeEach
    void setUp() {

        generadorSlotsAgenda = new GeneradorSlotsAgenda();

        prestacion = new Prestacion();
        prestacion.setDuracionMinima(Duration.ofMinutes(15));
        prestacion.setDuracionMaxima(Duration.ofMinutes(60));
        prestacion.setTiempoToleranciaSolicitud(Duration.ofHours(2));

    }

    @Test
    void generar_bloqueDivisibleExacto_generaLaCantidadDeSlotsEsperada() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(10, 0), prestacion, Duration.ofMinutes(30));

        List<GeneradorSlotsAgenda.SlotGenerado> slots = generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA);

        assertEquals(2, slots.size());
        assertEquals(LocalTime.of(9, 0), slots.get(0).horaDesde());
        assertEquals(LocalTime.of(9, 30), slots.get(0).horaHasta());
        assertEquals(LocalTime.of(9, 30), slots.get(1).horaDesde());
        assertEquals(LocalTime.of(10, 0), slots.get(1).horaHasta());

    }

    @Test
    void generar_duracionTurnoNoDivideElBloque_lanzaValidacionException() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(10, 0), prestacion, Duration.ofMinutes(40));

        ValidacionException excepcion = assertThrows(ValidacionException.class, () -> generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA));

        assertTrue(excepcion.getMessage() != null || !excepcion.getErrores().isEmpty());

    }

    @Test
    void generar_bloqueFueraDelHorarioDeAtencion_lanzaValidacionException() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(7, 0), LocalTime.of(9, 0), prestacion, Duration.ofMinutes(30));

        assertThrows(ValidacionException.class, () -> generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA));

    }

    @Test
    void generar_horaDesdeIgualHoraHasta_lanzaValidacionException() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(9, 0), prestacion, Duration.ofMinutes(30));

        assertThrows(ValidacionException.class, () -> generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA));

    }

    @Test
    void generar_duracionTurnoFueraDeLaDuracionMinimaOMaximaDeLaPrestacion_lanzaValidacionException() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(10, 30), prestacion, Duration.ofMinutes(90));

        assertThrows(ValidacionException.class, () -> generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA));

    }

    @Test
    void generar_bloqueSeSuperponeConOtroDelMismoLote_lanzaValidacionException() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque1 = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(10, 0), prestacion, Duration.ofMinutes(30));
        GeneradorSlotsAgenda.BloqueAGenerar bloque2 = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 30), LocalTime.of(11, 0), prestacion, Duration.ofMinutes(30));

        assertThrows(ValidacionException.class, () -> generadorSlotsAgenda.generar(
                List.of(bloque1, bloque2), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA));

    }

    @Test
    void generar_bloqueSeSuperponeConRangoYaExistenteEnBase_lanzaValidacionException() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(10, 0), prestacion, Duration.ofMinutes(30));

        Map<LocalDate, List<GeneradorSlotsAgenda.RangoHorario>> rangosExistentes = Map.of(
                LocalDate.of(2026, 9, 1), List.of(new GeneradorSlotsAgenda.RangoHorario(LocalTime.of(9, 30), LocalTime.of(10, 30))));

        assertThrows(ValidacionException.class, () -> generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, rangosExistentes, ZONA_HORARIA));

    }

    @Test
    void generar_calculaFechaLimiteReservaComoInicioDelSlotMenosLaTolerancia() {

        GeneradorSlotsAgenda.BloqueAGenerar bloque = new GeneradorSlotsAgenda.BloqueAGenerar(
                LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(9, 30), prestacion, Duration.ofMinutes(30));

        List<GeneradorSlotsAgenda.SlotGenerado> slots = generadorSlotsAgenda.generar(
                List.of(bloque), HORARIO_INICIO_ATENCION, HORARIO_FIN_ATENCION, Map.of(), ZONA_HORARIA);

        ZonedDateTime inicioSlotEsperado = ZonedDateTime.of(LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), ZONA_HORARIA);
        ZonedDateTime fechaLimiteEsperada = inicioSlotEsperado.minus(prestacion.getTiempoToleranciaSolicitud());

        assertEquals(1, slots.size());
        assertEquals(fechaLimiteEsperada, slots.get(0).fechaLimiteReserva());

    }

}
