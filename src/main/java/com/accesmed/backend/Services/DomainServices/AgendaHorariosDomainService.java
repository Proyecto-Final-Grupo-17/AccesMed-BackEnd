package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.AgendaHorarios;
import com.accesmed.backend.Repositories.AgendaHorariosRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de dominio y persistencia para la entidad {@code AgendaHorarios}. Nació de solo
 * lectura para el enforcement de la precondición restrictiva de baja de Prestación contra
 * horarios futuros ocupados; la Fase B lo extiende con el stack de escritura completo,
 * tocando únicamente {@code AgendaHorariosRepository}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaHorariosDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaHorariosRepository agendaHorariosRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Valida que una prestación no tenga horarios de agenda futuros (o de hoy)
     * ocupados. Precondición real de la baja restrictiva de deshabilitar una prestación.
     *
     * @param prestacionId {@code UUID} identificador de la prestación a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay horarios de
     *         agenda futuros ocupados de esa prestación
     */
    public void validateSinAgendaFuturaOcupada(UUID prestacionId) {

        if (agendaHorariosRepository.existsByPrestacionIdAndEstaOcupadaTrueAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(
                prestacionId, LocalDate.now())) {
            log.warn("No se pudo validar sin agenda futura ocupada para la prestación {}: tiene horarios de agenda futuros ocupados", prestacionId);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CON_AGENDA_OCUPADA",
                    "La prestación " + prestacionId + " tiene horarios de agenda futuros ocupados. No se puede deshabilitar.");
        }

    }

    /**
     * Guarda un horario de agenda en la base de datos.
     *
     * @param agendaHorarios {@code AgendaHorarios} entidad a persistir
     * @return {@code AgendaHorarios} el horario guardado
     */
    public AgendaHorarios saveAgendaHorarios(AgendaHorarios agendaHorarios) {

        log.debug("Guardando horario de agenda: día={}", agendaHorarios.getAgendaDia().getId());

        return agendaHorariosRepository.save(agendaHorarios);

    }

    /**
     * Guarda un lote de horarios de agenda en la base de datos.
     *
     * @param agendaHorarios {@code List<AgendaHorarios>} entidades a persistir
     * @return {@code List<AgendaHorarios>} los horarios guardados
     */
    public List<AgendaHorarios> saveAllAgendaHorarios(List<AgendaHorarios> agendaHorarios) {

        log.debug("Guardando lote de {} horario(s) de agenda", agendaHorarios.size());

        return agendaHorariosRepository.saveAll(agendaHorarios);

    }

    /**
     * Lista los horarios activos de un día de agenda.
     *
     * @param agendaDiaId {@code UUID} identificador del {@code AgendaDia}
     * @return {@code List<AgendaHorarios>} horarios activos de ese día
     */
    public List<AgendaHorarios> findByAgendaDiaId(UUID agendaDiaId) {

        return agendaHorariosRepository.findByAgendaDia_IdAndDeletedAtIsNull(agendaDiaId);

    }

    /**
     * Lista los horarios activos de un conjunto de días de agenda.
     *
     * @param agendaDiaIds {@code Collection<UUID>} identificadores de los {@code AgendaDia}
     * @return {@code List<AgendaHorarios>} horarios activos de esos días
     */
    public List<AgendaHorarios> findByAgendaDiaIds(Collection<UUID> agendaDiaIds) {

        if (agendaDiaIds.isEmpty()) {
            return List.of();
        }

        return agendaHorariosRepository.findByAgendaDia_IdInAndDeletedAtIsNull(agendaDiaIds);

    }

    /**
     * Busca horarios activos por un conjunto de identificadores.
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios
     * @return {@code List<AgendaHorarios>} horarios activos que existen entre esos identificadores
     */
    public List<AgendaHorarios> findAgendaHorariosByIds(Collection<UUID> ids) {

        if (ids.isEmpty()) {
            return List.of();
        }

        return agendaHorariosRepository.findByIdInAndDeletedAtIsNull(ids);

    }

    /**
     * Cuenta los horarios activos de toda una agenda médica. Usado para armar los conteos
     * de {@code listAgendaMedico}.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de horarios activos de esa agenda
     */
    public long countActivosByAgendaMedico(UUID agendaMedicoId) {

        return agendaHorariosRepository.countByAgendaDia_AgendaMedico_IdAndDeletedAtIsNull(agendaMedicoId);

    }

    /**
     * Valida que ninguno de los horarios indicados esté ocupado. Guarda restrictiva de
     * {@code updateAgendaMedico} y {@code updateVigenciaAgendaMedico}: excluir un día,
     * excluir horarios o adelantar el corte se rechaza si arrastra algún horario ocupado,
     * informando cuántos y hasta qué fecha, <b>antes de escribir nada</b>.
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios que la operación daría de baja
     * @throws ReglaNegocioException {@code ReglaNegocioException} si alguno de esos horarios está ocupado
     */
    public void validateSinOcupados(Collection<UUID> ids) {

        if (ids.isEmpty()) {
            return;
        }

        long cantidadOcupados = agendaHorariosRepository.countByIdInAndEstaOcupadaTrueAndDeletedAtIsNull(ids);

        if (cantidadOcupados > 0) {
            LocalDate fechaMaxima = agendaHorariosRepository.findMaxFechaOcupadaByIdIn(ids).orElse(null);
            log.warn("No se pudo aplicar la operación sobre la agenda: {} horario(s) ocupado(s), fecha máxima {}",
                    cantidadOcupados, fechaMaxima);
            throw new ReglaNegocioException(getClass(), "AGENDA_HORARIOS_CON_OCUPADOS",
                    "La operación arrastra " + cantidadOcupados + " horario(s) ocupado(s), el más lejano el "
                            + fechaMaxima + ". No se puede aplicar.");
        }

    }

    /**
     * Da de baja lógica un lote de horarios de agenda.
     *
     * @param agendaHorarios {@code List<AgendaHorarios>} horarios a dar de baja
     * @param deletedReason {@code String} motivo de la baja, o {@code null}
     */
    public void softDeleteAll(List<AgendaHorarios> agendaHorarios, String deletedReason) {

        Instant ahora = Instant.now();

        List<AgendaHorarios> horariosDadosDeBaja = agendaHorarios.stream()
                .peek(horario -> {
                    horario.setDeletedAt(ahora);
                    horario.setDeletedReason(deletedReason);
                })
                .collect(Collectors.toList());

        log.debug("Dando de baja lote de {} horario(s) de agenda", horariosDadosDeBaja.size());

        saveAllAgendaHorarios(horariosDadosDeBaja);

    }

    /**
     * Resultado de {@link #recalcularFechaLimiteReserva}: cuántos horarios se recalcularon
     * in place y cuántos quedaron con el plazo vencido y se dieron de baja.
     *
     * @param cantidadRecalculados {@code int} horarios a los que se les recalculó {@code fechaLimiteReserva}
     * @param cantidadDadosDeBaja {@code int} horarios dados de baja por quedar con el plazo vencido
     */
    public record ResultadoRecalculoTolerancia(int cantidadRecalculados, int cantidadDadosDeBaja) {

    }

    /**
     * Da de baja los horarios de agenda futuros y libres de una prestación. Cascada A4
     * (paso 2, habilitada por la Fase B): al deshabilitar una prestación, sus slots
     * futuros sin turno asociado quedan sin sentido. Los ocupados no se tocan (ya
     * validados como inexistentes por la precondición restrictiva antes de llegar acá).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @param deletedReason {@code String} motivo de la baja
     * @return {@code int} cantidad de horarios dados de baja
     */
    public int darDeBajaFuturosLibres(UUID prestacionId, LocalDate fechaDesde, String deletedReason) {

        List<AgendaHorarios> horariosLibres = agendaHorariosRepository
                .findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(prestacionId, fechaDesde);

        log.debug("Dando de baja {} horario(s) futuro(s) libre(s) de la prestación {}", horariosLibres.size(), prestacionId);

        softDeleteAll(horariosLibres, deletedReason);

        return horariosLibres.size();

    }

    /**
     * Da de baja los horarios de agenda futuros y libres de una prestación cuya duración
     * quedó fuera del nuevo rango {@code [duracionMinima, duracionMaxima]}. Cascada A6:
     * al cambiar las duraciones de una prestación, hay que revalidar los slots ya
     * generados. Los ocupados no se tocan nunca.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param duracionMinima {@code Duration} nueva duración mínima de la prestación
     * @param duracionMaxima {@code Duration} nueva duración máxima de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @return {@code int} cantidad de horarios dados de baja por quedar fuera de rango
     */
    public int darDeBajaFueraDeRangoDuracion(UUID prestacionId, Duration duracionMinima, Duration duracionMaxima, LocalDate fechaDesde) {

        List<AgendaHorarios> horariosLibres = agendaHorariosRepository
                .findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(prestacionId, fechaDesde);

        List<AgendaHorarios> fueraDeRango = horariosLibres.stream()
                .filter(horario -> {
                    Duration duracionSlot = Duration.between(horario.getHoraDesde(), horario.getHoraHasta());
                    return duracionSlot.compareTo(duracionMinima) < 0 || duracionSlot.compareTo(duracionMaxima) > 0;
                })
                .collect(Collectors.toList());

        log.debug("Dando de baja {} horario(s) fuera del nuevo rango de duración de la prestación {}", fueraDeRango.size(), prestacionId);

        softDeleteAll(fueraDeRango, "Fuera de rango de duración tras actualizar la prestación");

        return fueraDeRango.size();

    }

    /**
     * Recalcula {@code fechaLimiteReserva} de los horarios de agenda futuros y libres de
     * una prestación tras cambiar su tolerancia de solicitud. Cascada A6: los que quedan
     * con el plazo ya vencido se dan de baja en vez de actualizarse.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param nuevaTolerancia {@code Duration} nuevo {@code tiempoToleranciaSolicitud} de la prestación
     * @param fechaDesde {@code LocalDate} fecha a partir de la cual se considera "futuro"
     * @param zonaHorariaClinica {@code ZoneId} zona horaria de la clínica, con la que se
     *        resuelve el inicio del slot a instante absoluto
     * @return {@code ResultadoRecalculoTolerancia} cantidad recalculada y cantidad dada de baja
     */
    public ResultadoRecalculoTolerancia recalcularFechaLimiteReserva(UUID prestacionId, Duration nuevaTolerancia,
            LocalDate fechaDesde, ZoneId zonaHorariaClinica) {

        List<AgendaHorarios> horariosLibres = agendaHorariosRepository
                .findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndAgendaDia_FechaGreaterThanEqual(prestacionId, fechaDesde);

        ZonedDateTime ahora = ZonedDateTime.now();
        List<AgendaHorarios> aRecalcular = new ArrayList<>();
        List<AgendaHorarios> aDarDeBaja = new ArrayList<>();

        for (AgendaHorarios horario : horariosLibres) {
            ZonedDateTime inicioSlot = ZonedDateTime.of(horario.getAgendaDia().getFecha(), horario.getHoraDesde(), zonaHorariaClinica);
            ZonedDateTime nuevaFechaLimiteReserva = inicioSlot.minus(nuevaTolerancia);
            if (nuevaFechaLimiteReserva.isBefore(ahora)) {
                aDarDeBaja.add(horario);
            } else {
                horario.setFechaLimiteReserva(nuevaFechaLimiteReserva);
                aRecalcular.add(horario);
            }
        }

        log.debug("Recalculando fechaLimiteReserva de {} horario(s) y dando de baja {} por plazo vencido, prestación {}",
                aRecalcular.size(), aDarDeBaja.size(), prestacionId);

        if (!aRecalcular.isEmpty()) {
            saveAllAgendaHorarios(aRecalcular);
        }
        softDeleteAll(aDarDeBaja, "Plazo de reserva vencido tras actualizar la tolerancia de solicitud de la prestación");

        return new ResultadoRecalculoTolerancia(aRecalcular.size(), aDarDeBaja.size());

    }

    //endregion

}
