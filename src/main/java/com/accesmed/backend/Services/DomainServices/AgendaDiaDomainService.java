package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.AgendaDia;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Repositories.AgendaDiaRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code AgendaDia}. Excluir un día es
 * darlo de baja lógica; se gestiona con get-or-create desde {@code updateAgendaMedico},
 * respaldado por el único parcial {@code (agenda_medico_id, fecha)} del esquema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaDiaDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaDiaRepository agendaDiaRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un día de agenda en la base de datos.
     *
     * @param agendaDia {@code AgendaDia} entidad a persistir
     * @return {@code AgendaDia} el día guardado
     */
    public AgendaDia saveAgendaDia(AgendaDia agendaDia) {

        log.debug("Guardando día de agenda: agenda={}, fecha={}", agendaDia.getAgendaMedico().getId(), agendaDia.getFecha());

        return agendaDiaRepository.save(agendaDia);

    }

    /**
     * Busca el día activo de una agenda para una fecha dada, o crea uno nuevo si no
     * existe. Base del delta de composición de {@code updateAgendaMedico}: el día es
     * implícito en {@code horariosAAgregar}, nunca se agrega un día vacío por su cuenta.
     *
     * @param agendaMedico {@code AgendaMedico} agenda dueña del día
     * @param fecha {@code LocalDate} fecha del día
     * @return {@code AgendaDia} el día activo existente, o uno nuevo recién guardado
     */
    public AgendaDia findOrCreateAgendaDia(AgendaMedico agendaMedico, LocalDate fecha) {

        return agendaDiaRepository.findByAgendaMedico_IdAndFechaAndDeletedAtIsNull(agendaMedico.getId(), fecha)
                .orElseGet(() -> {
                    log.debug("Creando día de agenda nuevo: agenda={}, fecha={}", agendaMedico.getId(), fecha);
                    AgendaDia agendaDiaNueva = new AgendaDia();
                    agendaDiaNueva.setAgendaMedico(agendaMedico);
                    agendaDiaNueva.setFecha(fecha);
                    return saveAgendaDia(agendaDiaNueva);
                });

    }

    /**
     * Busca días activos por un conjunto de identificadores.
     *
     * @param ids {@code Collection<UUID>} identificadores de los días
     * @return {@code List<AgendaDia>} días activos que existen entre esos identificadores
     */
    public List<AgendaDia> findAgendaDiasByIds(Collection<UUID> ids) {

        log.debug("Buscando días de agenda por ids: {}", ids);

        return agendaDiaRepository.findByIdInAndDeletedAtIsNull(ids);

    }

    /**
     * Lista los días activos de una agenda.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code List<AgendaDia>} días activos de esa agenda
     */
    public List<AgendaDia> findDiasActivos(UUID agendaMedicoId) {

        log.debug("Buscando días activos de la agenda: {}", agendaMedicoId);

        return agendaDiaRepository.findByAgendaMedico_IdAndDeletedAtIsNull(agendaMedicoId);

    }

    /**
     * Lista los días activos posteriores a una fecha dada (excluida) de una agenda. Usado
     * por {@code updateVigenciaAgendaMedico} al adelantar el fin de vigencia.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @param fecha {@code LocalDate} fecha a partir de la cual (exclusive) se consideran "posteriores"
     * @return {@code List<AgendaDia>} días activos posteriores a esa fecha
     */
    public List<AgendaDia> findDiasPosteriores(UUID agendaMedicoId, LocalDate fecha) {

        log.debug("Buscando días de agenda posteriores a {}: agenda={}", fecha, agendaMedicoId);

        return agendaDiaRepository.findByAgendaMedico_IdAndFechaGreaterThanAndDeletedAtIsNull(agendaMedicoId, fecha);

    }

    /**
     * Cuenta los días activos de una agenda.
     *
     * @param agendaMedicoId {@code UUID} identificador de la agenda
     * @return {@code long} cantidad de días activos
     */
    public long countDiasActivos(UUID agendaMedicoId) {

        return agendaDiaRepository.countByAgendaMedico_IdAndDeletedAtIsNull(agendaMedicoId);

    }

    /**
     * Da de baja lógica un día de agenda.
     *
     * @param agendaDia {@code AgendaDia} día a dar de baja
     * @param deletedReason {@code String} motivo de la baja, o {@code null}
     */
    public void softDeleteAgendaDia(AgendaDia agendaDia, String deletedReason) {

        log.debug("Dando de baja día de agenda: id={}", agendaDia.getId());

        agendaDia.setDeletedAt(Instant.now());
        agendaDia.setDeletedReason(deletedReason);

        saveAgendaDia(agendaDia);

    }

    /**
     * Busca un día de agenda activo por su identificador.
     *
     * @param id {@code UUID} identificador del día
     * @return {@code AgendaDia} el día correspondiente al id, si está activo
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         un día activo con ese id
     */
    public AgendaDia findAgendaDiaActivoById(UUID id) {

        log.debug("Buscando día de agenda activo por id: {}", id);

        return agendaDiaRepository.findById(id)
                .filter(dia -> dia.getDeletedAt() == null)
                .orElseThrow(() -> {
                    log.warn("No se encontró el día de agenda activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "AGENDA_DIA_NO_ENCONTRADO",
                            "No existe un día de agenda activo con el id " + id);
                });

    }

    //endregion

}
