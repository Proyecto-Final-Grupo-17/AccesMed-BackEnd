package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Records.Especialidad.Criteria.EspecialidadCriteria;
import com.accesmed.backend.Records.Especialidad.Request.CreateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Request.UpdateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Response.CreateEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.GetEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.ListEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.SoftDeleteEspecialidadResponse;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.EspecialidadMapper;
import com.accesmed.backend.Services.QueryServices.EspecialidadQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso de Especialidad. Orquesta el flujo completo de los endpoints
 * (creación, actualización, baja) validando reglas de negocio y coordinando los services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspecialidadApp {

    //region ========== Dependencias ==========

    //Domain Services
    private final EspecialidadDomainService especialidadDomainService;
    private final MedicoDomainService medicoDomainService;
    private final PrestacionDomainService prestacionDomainService;

    //Mappers
    private final EspecialidadMapper especialidadMapper;

    //Query Services
    private final EspecialidadQueryService especialidadQueryService;
    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una especialidad nueva.
     *
     * @param createEspecialidadRequest {@code CreateEspecialidadRequest} datos de la especialidad a crear
     * @return {@code CreateEspecialidadResponse} la especialidad creada
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre ya existen
     */
    @Transactional
    public CreateEspecialidadResponse createEspecialidad(CreateEspecialidadRequest createEspecialidadRequest) {

        log.info("Creación de especialidad iniciada: código={}", createEspecialidadRequest.codigo());

        //Validar que el código y el nombre sean únicos
        especialidadDomainService.validateCodigoEspecialidadIsUnique(createEspecialidadRequest.codigo());
        especialidadDomainService.validateNombreEspecialidadIsUnique(createEspecialidadRequest.nombre());

        //Mapear y guardar
        Especialidad especialidadNueva = especialidadMapper.toEntity(createEspecialidadRequest);
        Especialidad especialidadGuardada = especialidadDomainService.saveEspecialidad(especialidadNueva);

        //Devolver response mapeado
        CreateEspecialidadResponse createEspecialidadResponse = especialidadMapper.toCreateResponse(especialidadGuardada);
        return createEspecialidadResponse;

    }

    /**
     * Actualiza una especialidad existente.
     *
     * @param updateEspecialidadRequest {@code UpdateEspecialidadRequest} datos a actualizar,
     *        incluyendo el id de la especialidad (ya validado contra la ruta en el Controller)
     * @return {@code GetEspecialidadResponse} la especialidad actualizada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la especialidad no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código o nombre son duplicados
     */
    @Transactional
    public GetEspecialidadResponse updateEspecialidad(UpdateEspecialidadRequest updateEspecialidadRequest) {

        UUID id = updateEspecialidadRequest.id();

        log.info("Actualización de especialidad iniciada: id={}", id);

        //Buscar la especialidad activa
        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadActivaById(id);

        //Validar unicidad de los campos que vinieron
        if (updateEspecialidadRequest.codigo() != null) {
            especialidadDomainService.validateCodigoEspecialidadIsUnique(updateEspecialidadRequest.codigo(), id);
        }
        if (updateEspecialidadRequest.nombre() != null) {
            especialidadDomainService.validateNombreEspecialidadIsUnique(updateEspecialidadRequest.nombre(), id);
        }

        //Aplicar los cambios y guardar
        especialidadMapper.updateEspecialidad(especialidadExistente, updateEspecialidadRequest);
        Especialidad especialidadActualizada = especialidadDomainService.saveEspecialidad(especialidadExistente);

        //Devolver response mapeado
        GetEspecialidadResponse getEspecialidadResponse = especialidadMapper.toGetResponse(especialidadActualizada);
        return getEspecialidadResponse;

    }

    /**
     * Da de baja una especialidad (baja lógica restrictiva).
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code SoftDeleteEspecialidadResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la especialidad no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si tiene médicos activos o
     *         prestaciones no deshabilitadas asociadas
     */
    @Transactional
    public SoftDeleteEspecialidadResponse softDeleteEspecialidad(UUID id) {

        log.info("Baja de especialidad iniciada: id={}", id);

        //Buscar la especialidad activa
        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadActivaById(id);

        //Validar que no tenga médicos activos ni prestaciones no deshabilitadas
        medicoDomainService.validateSinMedicosActivos(id);
        prestacionDomainService.validateSinPrestacionesActivas(id);

        //Dar de baja
        especialidadDomainService.softDeleteEspecialidad(especialidadExistente, "Baja de especialidad");

        //Devolver response mapeado
        SoftDeleteEspecialidadResponse softDeleteEspecialidadResponse = especialidadMapper.toSoftDeleteResponse(especialidadExistente);
        return softDeleteEspecialidadResponse;

    }

    /**
     * Busca la especialidad activa que cumple el criteria de filtrado dinámico
     * proporcionado. A diferencia de {@link #findEspecialidades}, devuelve una única
     * especialidad (no paginada) — pensado para criterios que identifican una especialidad
     * puntual (ej. {@code id.equals}).
     *
     * @param especialidadCriteria {@code EspecialidadCriteria} filtros a aplicar
     * @return {@code GetEspecialidadResponse} la especialidad encontrada
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna especialidad cumple el criteria
     */
    @Transactional(readOnly = true)
    public GetEspecialidadResponse findEspecialidadByCriteria(EspecialidadCriteria especialidadCriteria) {

        log.info("Búsqueda de especialidad iniciada: criteria={}", especialidadCriteria);

        Especialidad especialidadExistente = especialidadQueryService.findEspecialidadByCriteria(especialidadCriteria);

        GetEspecialidadResponse getEspecialidadResponse = especialidadMapper.toGetResponse(especialidadExistente);
        return getEspecialidadResponse;

    }

    /**
     * Lista especialidades activas según el criteria de filtrado dinámico proporcionado.
     *
     * @param especialidadCriteria {@code EspecialidadCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListEspecialidadResponse>} página de especialidades que cumplen el criteria
     */
    @Transactional(readOnly = true)
    public PageResponse<ListEspecialidadResponse> findEspecialidades(EspecialidadCriteria especialidadCriteria, Pageable pageable) {

        log.info("Listado de especialidades iniciado: criteria={}, page={}", especialidadCriteria, pageable);

        //Buscar especialidades que cumplen el criteria, paginadas
        Page<Especialidad> especialidadesPagina = especialidadQueryService.findByCriteria(especialidadCriteria, pageable);

        //Devolver response mapeado
        PageResponse<ListEspecialidadResponse> pageResponse = PageResponse.from(especialidadesPagina, especialidadMapper::toListResponse);
        return pageResponse;

    }

    //endregion

}
