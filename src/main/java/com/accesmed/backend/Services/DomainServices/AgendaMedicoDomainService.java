package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Repositories.AgendaMedicoRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code AgendaMedico}. No tiene baja
 * lógica: se gestiona por vigencia. El no solapamiento de períodos del mismo médico lo
 * refuerza además el esquema con {@code EXCLUDE USING gist} (última línea de defensa); esta
 * clase da el enforcement con mensaje legible antes de llegar a la base.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaMedicoDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaMedicoRepository agendaMedicoRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una agenda médica en la base de datos.
     *
     * @param agendaMedico {@code AgendaMedico} entidad a persistir
     * @return {@code AgendaMedico} la agenda guardada
     */
    public AgendaMedico saveAgendaMedico(AgendaMedico agendaMedico) {

        log.debug("Guardando agenda médica: médico={}", agendaMedico.getMedico().getId());

        return agendaMedicoRepository.save(agendaMedico);

    }

    /**
     * Busca una agenda médica por su identificador.
     *
     * @param id {@code UUID} identificador de la agenda
     * @return {@code AgendaMedico} la agenda correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una agenda con ese id
     */
    public AgendaMedico findAgendaMedicoById(UUID id) {

        log.debug("Buscando agenda médica por id: {}", id);

        return agendaMedicoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la agenda médica: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "AGENDA_MEDICO_NO_ENCONTRADA",
                            "No se encontró la agenda solicitada.");
                });

    }

    /**
     * Valida que el período {@code [desde, hasta]} (ambos extremos inclusivos) indicado no se solape con ningún otro
     * período de vigencia ya existente del médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param desde {@code LocalDate} inicio del período a validar
     * @param hasta {@code LocalDate} fin del período a validar
     * @param excludeId {@code UUID} identificador de agenda a excluir de la comparación
     *        (la propia agenda, en un update), o {@code null} en un alta
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el período se solapa con
     *         un período ya existente del médico
     */
    public void validateSinSolapamiento(UUID medicoId, LocalDate desde, LocalDate hasta, UUID excludeId) {

        if (agendaMedicoRepository.existsSolapamiento(medicoId, desde, hasta, excludeId)) {
            log.warn("No se pudo guardar la agenda del médico {}: el período [{}, {}] se solapa con un período existente",
                    medicoId, desde, hasta);
            throw new ReglaNegocioException(getClass(), "AGENDA_MEDICO_SOLAPADA",
                    "El período indicado se superpone con otra agenda ya cargada para este médico. Elegí fechas que no se pisen.");
        }

    }

    //endregion

}
