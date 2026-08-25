package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.IndicacionPrestacionTurno;
import com.accesmed.backend.Repositories.IndicacionPrestacionTurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code IndicacionPrestacionTurno}.
 * Encapsula guardar, buscar y la marcación de validación de las indicaciones
 * asociadas a un turno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicacionPrestacionTurnoDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionTurnoRepository indicacionPrestacionTurnoRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda todas las indicaciones de un turno en la base de datos.
     *
     * @param indicaciones {@code List<IndicacionPrestacionTurno>} entidades a persistir
     * @return {@code List<IndicacionPrestacionTurno>} las indicaciones guardadas
     */
    public List<IndicacionPrestacionTurno> saveAllIndicacionesPrestacionTurno(List<IndicacionPrestacionTurno> indicaciones) {

        log.debug("Guardando {} indicaciones del turno", indicaciones.size());

        return indicacionPrestacionTurnoRepository.saveAll(indicaciones);

    }

    /**
     * Busca todas las indicaciones activas de un turno.
     *
     * @param turnoId {@code UUID} identificador del turno
     * @return {@code List<IndicacionPrestacionTurno>} las indicaciones activas de ese turno
     */
    public List<IndicacionPrestacionTurno> findByTurnoId(UUID turnoId) {

        log.debug("Buscando indicaciones del turno: {}", turnoId);

        return indicacionPrestacionTurnoRepository.findByTurno_IdAndDeletedAtIsNull(turnoId);

    }

    /**
     * Marca las indicaciones que requieren validación como validadas, estableciendo
     * la fecha y hora actual de validación. Solo afecta a indicaciones donde
     * {@code indicacionPrestacion.requiereValidacion} es {@code true} y
     * {@code fechaHoraValidacion} aún es {@code null}.
     *
     * @param indicaciones {@code List<IndicacionPrestacionTurno>} indicaciones a procesar
     */
    public void marcarValidadas(List<IndicacionPrestacionTurno> indicaciones) {

        log.debug("Marcando {} indicaciones como validadas", indicaciones.size());

        List<IndicacionPrestacionTurno> indicacionesAActualizar = indicaciones.stream()
                .filter(ind -> ind.getIndicacionPrestacion().getRequiereValidacion()
                        && ind.getFechaHoraValidacion() == null)
                .toList();

        for (IndicacionPrestacionTurno indicacion : indicacionesAActualizar) {
            indicacion.setFechaHoraValidacion(ZonedDateTime.now());
        }

        if (!indicacionesAActualizar.isEmpty()) {
            saveAllIndicacionesPrestacionTurno(indicacionesAActualizar);
        }

    }

    //endregion

}
