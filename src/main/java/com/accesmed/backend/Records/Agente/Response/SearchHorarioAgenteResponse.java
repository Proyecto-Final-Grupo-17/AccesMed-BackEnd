package com.accesmed.backend.Records.Agente.Response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Horarios disponibles agrupados por día, médico y prestación, según los filtros de la búsqueda
 * ({@code GET /Agente/Horario}). Si no hay ninguno, {@code dias} viene vacío y {@code motivo} dice por qué.
 *
 * @param dias {@code List<DiaHorario>} días con horarios, en orden de fecha
 * @param hayMas {@code boolean} {@code true} si hay más días u horarios que no entraron: se pide con otra {@code pagina} o con filtros más finos
 * @param codigoMotivo {@code String} por qué no hay horarios ({@code NO_ENCONTRADO}, {@code NO_HACE_PRESTACION}, {@code NO_ATIENDE_ESE_DIA}, {@code TODO_OCUPADO}, {@code FUERA_DE_HORIZONTE}), o {@code null} si hay
 * @param motivo {@code String} el motivo en español para el paciente, o {@code null} si hay horarios
 */
public record SearchHorarioAgenteResponse(
        List<DiaHorario> dias,
        boolean hayMas,
        String codigoMotivo,
        String motivo
) {

    /**
     * Un día con horarios.
     *
     * @param fecha {@code LocalDate} día
     * @param fechaTexto {@code String} el día legible, con el día de la semana ({@code jueves 12/11/2026})
     * @param medicos {@code List<MedicoHorario>} médicos que atienden ese día con horarios libres
     */
    public record DiaHorario(LocalDate fecha, String fechaTexto, List<MedicoHorario> medicos) {
    }

    /**
     * Un médico con sus horarios libres de un día.
     *
     * @param nombre {@code String} nombre del médico
     * @param apellido {@code String} apellido del médico
     * @param matricula {@code String} matrícula del médico
     * @param especialidad {@code String} nombre de su especialidad
     * @param atiendeDesde {@code LocalTime} hora en que empieza a atender ese día
     * @param atiendeHasta {@code LocalTime} hora en que termina de atender ese día
     * @param prestaciones {@code List<PrestacionHorario>} prestaciones con horarios libres
     */
    public record MedicoHorario(String nombre, String apellido, String matricula, String especialidad,
            LocalTime atiendeDesde, LocalTime atiendeHasta, List<PrestacionHorario> prestaciones) {
    }

    /**
     * Una prestación con sus horarios libres (con tope).
     *
     * @param prestacion {@code String} nombre de la prestación
     * @param horarios {@code List<ListHorarioDisponibleAgenteResponse>} horarios libres, con su {@code slotId}
     * @param hayMas {@code boolean} {@code true} si hay más horarios de los que se listan
     */
    public record PrestacionHorario(String prestacion, List<ListHorarioDisponibleAgenteResponse> horarios,
            boolean hayMas) {
    }

}
