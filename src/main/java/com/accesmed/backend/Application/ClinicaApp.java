package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Records.Clinica.Request.UpdateClinicaRequest;
import com.accesmed.backend.Records.Clinica.Response.GetClinicaResponse;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.ClinicaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * Caso de uso de Clínica. Orquesta el flujo de los endpoints (obtención y actualización)
 * de la instancia única de configuración, validando reglas de negocio y coordinando el
 * service de {@code Clinica}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicaApp {

    //region ========== Dependencias o inyecciones ==========

    private final ClinicaDomainService clinicaDomainService;
    private final ClinicaMapper clinicaMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Actualiza la instancia única de clínica. Un campo en {@code null} deja ese dato
     * sin tocar; los horarios y los días de vigencia de agenda se validan con sus valores
     * efectivos (el que viene en el request, o si no vino, el ya guardado).
     *
     * @param updateClinicaRequest {@code UpdateClinicaRequest} datos a actualizar
     * @return {@code GetClinicaResponse} la clínica actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la fila de
     *         configuración no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el horario de inicio no es
     *         anterior al de fin, o si los días mínimos de vigencia de agenda superan a los máximos
     */
    @Transactional
    public GetClinicaResponse updateClinica(UpdateClinicaRequest updateClinicaRequest) {

        log.info("Actualización de clínica iniciada");

        //Buscar la instancia única de clínica
        Clinica clinicaExistente = clinicaDomainService.findClinica();

        //Resolver valores efectivos y validar los horarios y los días de vigencia de agenda
        LocalTime horarioInicioEfectivo = updateClinicaRequest.horarioInicioAtencion() != null
                ? updateClinicaRequest.horarioInicioAtencion() : clinicaExistente.getHorarioInicioAtencion();
        LocalTime horarioFinEfectivo = updateClinicaRequest.horarioFinAtencion() != null
                ? updateClinicaRequest.horarioFinAtencion() : clinicaExistente.getHorarioFinAtencion();
        clinicaDomainService.validateHorarioAtencion(horarioInicioEfectivo, horarioFinEfectivo);

        Integer diasMinimosEfectivo = updateClinicaRequest.diasMinimosVigenciaAgenda() != null
                ? updateClinicaRequest.diasMinimosVigenciaAgenda() : clinicaExistente.getDiasMinimosVigenciaAgenda();
        Integer diasMaximosEfectivo = updateClinicaRequest.diasMaximosVigenciaAgenda() != null
                ? updateClinicaRequest.diasMaximosVigenciaAgenda() : clinicaExistente.getDiasMaximosVigenciaAgenda();
        clinicaDomainService.validateDiasVigenciaAgenda(diasMinimosEfectivo, diasMaximosEfectivo);

        //Aplicar los cambios y guardar
        clinicaMapper.updateClinica(clinicaExistente, updateClinicaRequest);
        Clinica clinicaActualizada = clinicaDomainService.saveClinica(clinicaExistente);

        //Devolver response mapeado
        GetClinicaResponse getClinicaResponse = clinicaMapper.toGetResponse(clinicaActualizada);
        return getClinicaResponse;

    }

    /**
     * Busca la instancia única de clínica.
     *
     * @return {@code GetClinicaResponse} la clínica
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la fila de
     *         configuración no existe
     */
    @Transactional(readOnly = true)
    public GetClinicaResponse findClinica() {

        log.info("Búsqueda de clínica iniciada");

        //Buscar la instancia única de clínica
        Clinica clinicaExistente = clinicaDomainService.findClinica();

        //Devolver response mapeado
        GetClinicaResponse getClinicaResponse = clinicaMapper.toGetResponse(clinicaExistente);
        return getClinicaResponse;

    }

    //endregion

}
