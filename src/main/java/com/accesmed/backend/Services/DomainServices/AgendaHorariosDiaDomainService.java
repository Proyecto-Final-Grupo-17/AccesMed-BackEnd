package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Repositories.AgendaHorariosDiaRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de dominio y persistencia para la entidad {@code AgendaHorariosDia}. Nació de
 * solo lectura para el enforcement de la precondición restrictiva de baja de Prestación
 * contra horarios futuros ocupados; la Fase B lo extiende con el stack de escritura
 * completo, tocando únicamente {@code AgendaHorariosDiaRepository}. Absorbe también la
 * lógica de conteo por día: un "día" es, simplemente, una fecha distinta entre los
 * horarios activos, no una entidad propia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaHorariosDiaDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaHorariosDiaRepository agendaHorariosDiaRepository;

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

        if (agendaHorariosDiaRepository.existsByPrestacion_IdAndEstaOcupadaTrueAndDeletedAtIsNullAndFechaGreaterThanEqual(
                prestacionId, LocalDate.now())) {
            log.warn("No se pudo validar sin agenda futura ocupada para la prestación {}: tiene horarios de agenda futuros ocupados", prestacionId);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CON_AGENDA_OCUPADA",
                    "La prestación " + prestacionId + " tiene horarios de agenda futuros ocupados. No se puede deshabilitar.");
        }

    }

    /**
     * Guarda un horario de agenda en la base de datos.
     *
     * @param agendaHorariosDia {@code AgendaHorariosDia} entidad a persistir
     * @return {@code AgendaHorariosDia} el horario guardado
     */
    public AgendaHorariosDia saveAgendaHorariosDia(AgendaHorariosDia agendaHorariosDia) {

        log.debug("Guardando horario de agenda: agenda={}, fecha={}",
                agendaHorariosDia.getAgendaMedico().getId(), agendaHorariosDia.getFecha());

        return agendaHorariosDiaRepository.save(agendaHorariosDia);

    }

    /**
     * Guarda un lote de horarios de agenda en la base de datos.
     *
     * @param agendaHorariosDia {@code List<AgendaHorariosDia>} entidades a persistir
     * @return {@code List<AgendaHorariosDia>} los horarios guardados
     */
    public List<AgendaHorariosDia> saveAllAgendaHorariosDia(List<AgendaHorariosDia> agendaHorariosDia) {

        log.debug("Guardando lote de {} horario(s) de agenda", agendaHorariosDia.size());

        return agendaHorariosDiaRepository.saveAll(agendaHorariosDia);

    }

    /**
     * Lista los horarios activos de una agenda completa. Base de {@code getAgendaMedico}.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code List<AgendaHorariosDia>} horarios activos de esa agenda
     */
    public List<AgendaHorariosDia> findByAgendaMedicoId(UUID agendaMedicoId) {

        log.debug("Buscando horarios activos de la agenda: {}", agendaMedicoId);

        return agendaHorariosDiaRepository.findByAgendaMedico_IdAndDeletedAtIsNull(agendaMedicoId);

    }

    /**
     * Lista los horarios activos de una agenda para un conjunto de fechas. Base de la
     * resolución de {@code fechasAExcluir} (get por baja) y de {@code aplicarHorariosAAgregar}
     * (rangos ya ocupados de las fechas a agregar), ambos en {@code updateAgendaMedico}.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fechas {@code Collection<LocalDate>} fechas a buscar
     * @return {@code List<AgendaHorariosDia>} horarios activos de esa agenda en esas fechas
     */
    public List<AgendaHorariosDia> findByAgendaMedicoIdAndFechas(UUID agendaMedicoId, Collection<LocalDate> fechas) {

        if (fechas.isEmpty()) {
            return List.of();
        }

        log.debug("Buscando horarios activos de la agenda {} en las fechas: {}", agendaMedicoId, fechas);

        return agendaHorariosDiaRepository.findByAgendaMedico_IdAndFechaInAndDeletedAtIsNull(agendaMedicoId, fechas);

    }

    /**
     * Lista los horarios activos de una agenda posteriores a una fecha dada (excluida).
     * Usado por {@code updateVigenciaAgendaMedico} al adelantar el fin de vigencia.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fecha {@code LocalDate} fecha a partir de la cual (exclusive) se consideran "posteriores"
     * @return {@code List<AgendaHorariosDia>} horarios activos posteriores a esa fecha
     */
    public List<AgendaHorariosDia> findHorariosPosteriores(UUID agendaMedicoId, LocalDate fecha) {

        log.debug("Buscando horarios de agenda posteriores a {}: agenda={}", fecha, agendaMedicoId);

        return agendaHorariosDiaRepository.findByAgendaMedico_IdAndFechaGreaterThanAndDeletedAtIsNull(agendaMedicoId, fecha);

    }

    /**
     * Busca horarios activos por un conjunto de identificadores. Usado para resolver
     * {@code horariosAExcluir} de {@code updateAgendaMedico}.
     *
     * @param ids {@code Collection<UUID>} identificadores de los horarios
     * @return {@code List<AgendaHorariosDia>} horarios activos que existen entre esos identificadores
     */
    public List<AgendaHorariosDia> findAgendaHorariosByIds(Collection<UUID> ids) {

        if (ids.isEmpty()) {
            return List.of();
        }

        return agendaHorariosDiaRepository.findByIdInAndDeletedAtIsNull(ids);

    }

    /**
     * Cuenta los horarios activos de toda una agenda médica. Usado para armar los conteos
     * de {@code listAgendaMedico} cuando se consulta una sola agenda.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de horarios activos de esa agenda
     */
    public long countActivosByAgendaMedico(UUID agendaMedicoId) {

        return agendaHorariosDiaRepository.countByAgendaMedico_IdAndDeletedAtIsNull(agendaMedicoId);

    }

    /**
     * Cuenta las fechas distintas con horarios activos de una agenda.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de fechas distintas con horarios activos
     */
    public long countDiasActivos(UUID agendaMedicoId) {

        return agendaHorariosDiaRepository.countDistinctFechasByAgendaMedico(agendaMedicoId);

    }

    /**
     * Cuenta, en una sola consulta agrupada, las fechas distintas y los horarios activos
     * de cada agenda de un lote, indexados por id de agenda. Cierra el N+1 de
     * {@code listAgendaMedico} (Fase 3 bis #1): antes se pedían ambos conteos por fila de
     * página.
     *
     * @param agendaMedicoIds {@code Collection<UUID>} identificadores de las agendas
     * @return {@code Map<UUID, AgendaHorariosDiaRepository.ConteoAgendaMedico>} los conteos, indexados por id de agenda
     */
    public Map<UUID, AgendaHorariosDiaRepository.ConteoAgendaMedico> countDiasYHorariosActivosByAgendaMedicoIds(Collection<UUID> agendaMedicoIds) {

        if (agendaMedicoIds.isEmpty()) {
            return Map.of();
        }

        log.debug("Buscando conteos de días y horarios activos de {} agenda(s)", agendaMedicoIds.size());

        return agendaHorariosDiaRepository.countDiasYHorariosActivosByAgendaMedicoIds(agendaMedicoIds).stream()
                .collect(Collectors.toMap(AgendaHorariosDiaRepository.ConteoAgendaMedico::getAgendaMedicoId, conteo -> conteo));

    }

    /**
     * Valida que ninguno de los horarios indicados esté ocupado. Guarda restrictiva de
     * {@code updateAgendaMedico} y {@code updateVigenciaAgendaMedico}: excluir fechas,
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

        long cantidadOcupados = agendaHorariosDiaRepository.countByIdInAndEstaOcupadaTrueAndDeletedAtIsNull(ids);

        if (cantidadOcupados > 0) {
            LocalDate fechaMaxima = agendaHorariosDiaRepository.findMaxFechaOcupadaByIdIn(ids).orElse(null);
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
     * @param agendaHorariosDia {@code List<AgendaHorariosDia>} horarios a dar de baja
     * @param deletedReason {@code String} motivo de la baja, o {@code null}
     */
    public void softDeleteAll(List<AgendaHorariosDia> agendaHorariosDia, String deletedReason) {

        Instant ahora = Instant.now();

        agendaHorariosDia.forEach(horario -> {
            horario.setDeletedAt(ahora);
            horario.setDeletedReason(deletedReason);
        });

        log.debug("Dando de baja lote de {} horario(s) de agenda", agendaHorariosDia.size());

        saveAllAgendaHorariosDia(agendaHorariosDia);

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

        List<AgendaHorariosDia> horariosLibres = agendaHorariosDiaRepository
                .findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndFechaGreaterThanEqual(prestacionId, fechaDesde);

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

        List<AgendaHorariosDia> horariosLibres = agendaHorariosDiaRepository
                .findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndFechaGreaterThanEqual(prestacionId, fechaDesde);

        List<AgendaHorariosDia> fueraDeRango = horariosLibres.stream()
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

        List<AgendaHorariosDia> horariosLibres = agendaHorariosDiaRepository
                .findByPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNullAndFechaGreaterThanEqual(prestacionId, fechaDesde);

        ZonedDateTime ahora = ZonedDateTime.now();
        List<AgendaHorariosDia> aRecalcular = new ArrayList<>();
        List<AgendaHorariosDia> aDarDeBaja = new ArrayList<>();

        for (AgendaHorariosDia horario : horariosLibres) {
            ZonedDateTime inicioSlot = ZonedDateTime.of(horario.getFecha(), horario.getHoraDesde(), zonaHorariaClinica);
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
            saveAllAgendaHorariosDia(aRecalcular);
        }
        softDeleteAll(aDarDeBaja, "Plazo de reserva vencido tras actualizar la tolerancia de solicitud de la prestación");

        return new ResultadoRecalculoTolerancia(aRecalcular.size(), aDarDeBaja.size());

    }

    /**
     * Busca un horario de agenda disponible por su identificador, validando que exista,
     * que no esté ocupado y que no esté dado de baja. Se verifica implícitamente que
     * pertenece al médico y la prestación indicados.
     *
     * @param slotId {@code UUID} identificador del horario
     * @param medicoId {@code UUID} identificador del médico propietario del horario
     * @param prestacionId {@code UUID} identificador de la prestación del horario
     * @return {@code AgendaHorariosDia} el horario disponible
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el
     *         horario no existe, está ocupado, está dado de baja, o no pertenece al médico/prestación
     */
    public AgendaHorariosDia findAgendaHorarioDisponible(UUID slotId, UUID medicoId, UUID prestacionId) {

        log.debug("Buscando horario disponible: id={}, médico={}, prestación={}", slotId, medicoId, prestacionId);

        return agendaHorariosDiaRepository
                .findByIdAndAgendaMedico_Medico_IdAndPrestacion_IdAndEstaOcupadaFalseAndDeletedAtIsNull(
                        slotId, medicoId, prestacionId)
                .orElseThrow(() -> {
                    log.warn("Horario no disponible: id={}, médico={}, prestación={}", slotId, medicoId, prestacionId);
                    return new com.accesmed.backend.Services.Errors.RecursoNoEncontradoException(getClass(),
                            "AGENDA_HORARIO_NO_DISPONIBLE",
                            "El horario indicado no está disponible (no existe, está ocupado o está dado de baja)");
                });

    }

    /**
     * Marca un horario como ocupado. Guarda el cambio inmediatamente.
     *
     * @param slot {@code AgendaHorariosDia} horario a marcar como ocupado
     */
    public void occupyAgendaHorario(AgendaHorariosDia slot) {

        log.debug("Marcando horario como ocupado: id={}", slot.getId());

        slot.setEstaOcupada(true);
        saveAgendaHorariosDia(slot);

    }

    /**
     * Marca un horario como libre. Guarda el cambio inmediatamente.
     *
     * @param slot {@code AgendaHorariosDia} horario a liberar
     */
    public void releaseAgendaHorario(AgendaHorariosDia slot) {

        log.debug("Liberando horario: id={}", slot.getId());

        slot.setEstaOcupada(false);
        saveAgendaHorariosDia(slot);

    }

    //endregion

}
