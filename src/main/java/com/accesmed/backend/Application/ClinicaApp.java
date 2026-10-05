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
     * sin tocar; el horario de atención se valida con sus valores efectivos (el que viene
     * en el request, o si no vino, el ya guardado). El horizonte de reserva
     * (`diasMaximosAnticipacionReserva`) queda cubierto por Bean Validation
     * ({@code @Min(1)}), sin regla de negocio adicional.
     *
     * @param updateClinicaRequest {@code UpdateClinicaRequest} datos a actualizar
     * @return {@code GetClinicaResponse} la clínica actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la fila de
     *         configuración no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el horario de inicio no es
     *         anterior al de fin, o si la zona horaria no es un identificador IANA válido
     */
    @Transactional
    public GetClinicaResponse updateClinica(UpdateClinicaRequest updateClinicaRequest) {

        log.info("Actualización de clínica iniciada");

        //Buscar la instancia única de clínica
        Clinica clinicaExistente = clinicaDomainService.findClinica();

        //Resolver valores efectivos y validar el horario de atención
        LocalTime horarioInicioEfectivo = updateClinicaRequest.horarioInicioAtencion() != null
                ? updateClinicaRequest.horarioInicioAtencion() : clinicaExistente.getHorarioInicioAtencion();
        LocalTime horarioFinEfectivo = updateClinicaRequest.horarioFinAtencion() != null
                ? updateClinicaRequest.horarioFinAtencion() : clinicaExistente.getHorarioFinAtencion();
        clinicaDomainService.validateHorarioAtencion(horarioInicioEfectivo, horarioFinEfectivo);

        //Validar la zona horaria solo si el request la trae: si no viene, la guardada ya es válida
        if (updateClinicaRequest.zonaHoraria() != null) {
            clinicaDomainService.validateZonaHoraria(updateClinicaRequest.zonaHoraria());
        }

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
