package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.MedicoPrestacion.Request.AssignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Request.UnassignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Response.GetMedicoPrestacionResponse;
import com.accesmed.backend.Records.MedicoPrestacion.Response.UnassignMedicoPrestacionResponse;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.MedicoPrestacionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso de Médico-Prestación. Orquesta el flujo de asignar y desasignar una
 * prestación a un médico, validando reglas de negocio y coordinando los services de
 * {@code MedicoPrestacion}, {@code Medico}, {@code Prestacion} y {@code Turno}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoPrestacionApp {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final TurnoDomainService turnoDomainService;
    private final MedicoPrestacionMapper medicoPrestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna una prestación existente a un médico existente, abriendo un período de
     * vigencia nuevo.
     *
     * @param assignMedicoPrestacionRequest {@code AssignMedicoPrestacionRequest} datos de la asignación
     * @return {@code GetMedicoPrestacionResponse} la asignación creada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el médico o
     *         la prestación no existen (activos)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si las especialidades no coinciden
     *         o el período se solapa con una vigencia ya existente entre ambos
     */
    @Transactional
    public GetMedicoPrestacionResponse assignPrestacion(AssignMedicoPrestacionRequest assignMedicoPrestacionRequest) {

        log.info("Asignación de prestación a médico iniciada: médico={}, prestación={}",
                assignMedicoPrestacionRequest.medicoId(), assignMedicoPrestacionRequest.prestacionId());

        //Buscar médico y prestación activos
        Medico medicoExistente = medicoDomainService.findMedicoActivoById(assignMedicoPrestacionRequest.medicoId());
        Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaById(assignMedicoPrestacionRequest.prestacionId());

        //Resolver la fecha de inicio de vigencia efectiva (ausente = ahora)
        ZonedDateTime fechaInicioVigenciaEfectiva = assignMedicoPrestacionRequest.fechaInicioVigencia() != null
                ? assignMedicoPrestacionRequest.fechaInicioVigencia() : ZonedDateTime.now();

        //Validar especialidad coincidente y que el período no se solape con una vigencia existente
        medicoPrestacionDomainService.validateEspecialidadCoincide(medicoExistente, prestacionExistente);
        medicoPrestacionDomainService.validateSinSolapamiento(
                medicoExistente.getId(), prestacionExistente.getId(), fechaInicioVigenciaEfectiva, null);

        //Mapear, completar relaciones y fecha de inicio de vigencia, y guardar
        MedicoPrestacion medicoPrestacionNueva = medicoPrestacionMapper.toEntity(assignMedicoPrestacionRequest);
        medicoPrestacionNueva.setMedico(medicoExistente);
        medicoPrestacionNueva.setPrestacion(prestacionExistente);
        medicoPrestacionNueva.setFechaInicioVigencia(fechaInicioVigenciaEfectiva);
        MedicoPrestacion medicoPrestacionGuardada = medicoPrestacionDomainService.saveMedicoPrestacion(medicoPrestacionNueva);

        //Devolver response mapeado
        GetMedicoPrestacionResponse getMedicoPrestacionResponse = medicoPrestacionMapper.toGetResponse(medicoPrestacionGuardada);
        return getMedicoPrestacionResponse;

    }

    /**
     * Desasigna una prestación de un médico: cierra el período de vigencia de la
     * asignación (no la borra). Valida el piso duro contra turnos vivos del par
     * médico-prestación antes de cerrar.
     * @param unassignMedicoPrestacionRequest {@code UnassignMedicoPrestacionRequest} id de la
     *        asignación y fecha de corte (ausente = ahora)
     * @return {@code UnassignMedicoPrestacionResponse} la confirmación del cierre de vigencia
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la asignación
     *         vigente no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la fecha de corte es anterior
     *         a la fecha de inicio de algún turno vivo del par médico-prestación
     */
    @Transactional
    public UnassignMedicoPrestacionResponse unassignPrestacion(UnassignMedicoPrestacionRequest unassignMedicoPrestacionRequest) {

        log.info("Desasignación de prestación de médico iniciada: id={}", unassignMedicoPrestacionRequest.id());

        //Buscar la asignación vigente
        MedicoPrestacion medicoPrestacionExistente = medicoPrestacionDomainService.findMedicoPrestacionVigenteById(unassignMedicoPrestacionRequest.id(), ZonedDateTime.now());

        //Resolver la fecha de corte efectiva (ausente = ahora)
        ZonedDateTime fechaFinVigenciaEfectiva = unassignMedicoPrestacionRequest.fechaFinVigencia() != null
                ? unassignMedicoPrestacionRequest.fechaFinVigencia() : ZonedDateTime.now();

        //Piso duro: la fecha de corte no puede ser anterior al inicio de ningún turno vivo del par
        Optional<ZonedDateTime> fechaMaximaTurnoVivo = turnoDomainService.findMaxFechaHoraInicioTurnoVivo(
                medicoPrestacionExistente.getMedico().getId(), medicoPrestacionExistente.getPrestacion().getId());
        if (fechaMaximaTurnoVivo.isPresent() && fechaFinVigenciaEfectiva.isBefore(fechaMaximaTurnoVivo.get())) {
            log.warn("No se pudo cerrar la vigencia {}: fecha de corte {} anterior al turno vivo más lejano {}",
                    unassignMedicoPrestacionRequest.id(), fechaFinVigenciaEfectiva, fechaMaximaTurnoVivo.get());
            throw new ReglaNegocioException(getClass(), "MEDICO_PRESTACION_CORTE_ANTERIOR_A_TURNO",
                    "La fecha de corte no puede ser anterior al turno vivo más lejano del par médico-prestación, el "
                            + fechaMaximaTurnoVivo.get() + ".");
        }

        //Cerrar la vigencia
        medicoPrestacionDomainService.cerrarVigenciaMedicoPrestacion(medicoPrestacionExistente, fechaFinVigenciaEfectiva);

        //Devolver response mapeado
        UnassignMedicoPrestacionResponse unassignMedicoPrestacionResponse =
                medicoPrestacionMapper.toUnassignResponse(medicoPrestacionExistente);
        return unassignMedicoPrestacionResponse;

    }

    //endregion

}
