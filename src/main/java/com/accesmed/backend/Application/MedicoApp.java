package com.accesmed.backend.Application;

import com.accesmed.backend.Application.Ports.GestionUsuarioPort;
import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.Medico.Request.AsignarPrestacionAnidadaRequest;
import com.accesmed.backend.Records.Medico.Request.CreateMedicoRequest;
import com.accesmed.backend.Records.Medico.Request.UpdateMedicoRequest;
import com.accesmed.backend.Records.Medico.Response.CreateMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.GetMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.GetPrestacionAnidadaResponse;
import com.accesmed.backend.Records.Medico.Response.SoftDeleteMedicoResponse;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.TurnoDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.MedicoMapper;
import com.accesmed.backend.Services.Mappers.MedicoPrestacionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Médico. Orquesta el flujo completo de los endpoints (creación atómica
 * con sus prestaciones, actualización, baja restrictiva) validando reglas de negocio y
 * coordinando los services de {@code Medico}, {@code MedicoPrestacion}, {@code Especialidad},
 * {@code Prestacion} y {@code Turno}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoApp {

    //region ========== Dependencias ==========

    //Domain Services
    private final MedicoDomainService medicoDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final EspecialidadDomainService especialidadDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final TurnoDomainService turnoDomainService;

    //Mappers
    private final MedicoMapper medicoMapper;
    private final MedicoPrestacionMapper medicoPrestacionMapper;

    //Services cross-cutting
    private final AutorizacionService autorizacionService;
    private final GestionUsuarioPort gestionUsuarioPort;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un médico nuevo junto con las prestaciones existentes que atiende, en una
     * única transacción atómica. Opcionalmente, crea su usuario de acceso si
     * {@code createMedicoRequest.crearUsuario()} es {@code true}.
     *
     * @param createMedicoRequest {@code CreateMedicoRequest} datos del médico y sus prestaciones
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada del usuario que realiza la operación
     * @return {@code CreateMedicoResponse} el médico creado, con sus prestaciones asignadas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la especialidad
     *         o alguna prestación no existen (activas)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la matrícula, DNI o email ya
     *         existen, o si la especialidad de alguna prestación no coincide con la del médico
     */
    @Transactional
    public CreateMedicoResponse createMedico(CreateMedicoRequest createMedicoRequest, UsuarioDetails usuarioDetails) {

        log.info("Creación de médico iniciada: matrícula={}", createMedicoRequest.matricula());

        //Validar que la matrícula, el DNI y el email sean únicos
        medicoDomainService.validateMatriculaMedicoIsUnique(createMedicoRequest.matricula());
        medicoDomainService.validateDniMedicoIsUnique(createMedicoRequest.dni());
        medicoDomainService.validateEmailMedicoIsUnique(createMedicoRequest.email());

        //Buscar la especialidad activa, mapear y guardar el médico
        Especialidad especialidadExistente = especialidadDomainService.findEspecialidadActivaById(createMedicoRequest.especialidadId());
        Medico medicoNuevo = medicoMapper.toEntity(createMedicoRequest);
        medicoNuevo.setEspecialidad(especialidadExistente);
        Medico medicoGuardado = medicoDomainService.saveMedico(medicoNuevo);

        //Crear usuario de acceso si fue solicitado
        if (Boolean.TRUE.equals(createMedicoRequest.crearUsuario())) {
            autorizacionService.requireAuthority(usuarioDetails, Permiso.USER_ALTA);
            gestionUsuarioPort.asignarUsuarioAMedico(medicoGuardado.getId(), medicoGuardado.getEmail());
        }

        //Asignar cada prestación anidada
        List<GetPrestacionAnidadaResponse> prestacionesResponse = new ArrayList<>();
        List<AsignarPrestacionAnidadaRequest> prestacionesRequest = createMedicoRequest.prestaciones();
        if (prestacionesRequest != null) {
            for (AsignarPrestacionAnidadaRequest prestacionAnidada : prestacionesRequest) {
                Prestacion prestacionExistente = prestacionDomainService.findPrestacionActivaById(prestacionAnidada.prestacionId());
                medicoPrestacionDomainService.validateEspecialidadCoincide(medicoGuardado, prestacionExistente);

                //Resolver la fecha de inicio de vigencia efectiva (ausente = ahora)
                ZonedDateTime fechaInicioVigenciaEfectiva = prestacionAnidada.fechaInicioVigencia() != null
                        ? prestacionAnidada.fechaInicioVigencia() : ZonedDateTime.now();

                MedicoPrestacion medicoPrestacionNueva = medicoPrestacionMapper.toEntity(prestacionAnidada);
                medicoPrestacionNueva.setMedico(medicoGuardado);
                medicoPrestacionNueva.setPrestacion(prestacionExistente);
                medicoPrestacionNueva.setFechaInicioVigencia(fechaInicioVigenciaEfectiva);

                MedicoPrestacion medicoPrestacionGuardada = medicoPrestacionDomainService.saveMedicoPrestacion(medicoPrestacionNueva);
                prestacionesResponse.add(medicoPrestacionMapper.toGetPrestacionAnidadaResponse(medicoPrestacionGuardada));
            }
        }

        //Devolver response mapeado
        CreateMedicoResponse createMedicoResponse = medicoMapper.toCreateResponse(medicoGuardado, prestacionesResponse);
        return createMedicoResponse;

    }

    /**
     * Actualiza un médico existente.
     *
     * @param updateMedicoRequest {@code UpdateMedicoRequest} datos a actualizar, incluyendo
     *        el id del médico (ya validado contra la ruta en el Controller)
     * @return {@code GetMedicoResponse} el médico actualizado, con sus prestaciones asignadas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el médico o la
     *         nueva especialidad no existen (activos)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si la matrícula, DNI o email son duplicados
     */
    @Transactional
    public GetMedicoResponse updateMedico(UpdateMedicoRequest updateMedicoRequest) {

        UUID id = updateMedicoRequest.id();

        log.info("Actualización de médico iniciada: id={}", id);

        //Buscar el médico activo
        Medico medicoExistente = medicoDomainService.findMedicoActivoById(id);

        //Validar unicidad de los campos que vinieron
        if (updateMedicoRequest.matricula() != null) {
            medicoDomainService.validateMatriculaMedicoIsUnique(updateMedicoRequest.matricula(), id);
        }
        if (updateMedicoRequest.dni() != null) {
            medicoDomainService.validateDniMedicoIsUnique(updateMedicoRequest.dni(), id);
        }
        if (updateMedicoRequest.email() != null) {
            medicoDomainService.validateEmailMedicoIsUnique(updateMedicoRequest.email(), id);
        }

        //Aplicar los cambios, resolver la especialidad si vino, y guardar
        medicoMapper.updateMedico(medicoExistente, updateMedicoRequest);
        if (updateMedicoRequest.especialidadId() != null) {
            Especialidad especialidadExistente = especialidadDomainService.findEspecialidadActivaById(updateMedicoRequest.especialidadId());
            medicoExistente.setEspecialidad(especialidadExistente);
        }
        Medico medicoActualizado = medicoDomainService.saveMedico(medicoExistente);

        //Devolver response mapeado, con las prestaciones vigentes del médico
        List<GetPrestacionAnidadaResponse> prestacionesResponse = medicoPrestacionMapper
                .toGetPrestacionAnidadaResponses(medicoPrestacionDomainService.findAsignacionesVigentesByMedico(id, ZonedDateTime.now()));
        GetMedicoResponse getMedicoResponse = medicoMapper.toGetResponse(medicoActualizado, prestacionesResponse);
        return getMedicoResponse;

    }

    /**
     * Da de baja un médico (baja lógica restrictiva contra turnos vivos).
     * Además, desactiva su usuario de acceso si existe.
     *
     * @param id {@code UUID} identificador del médico
     * @return {@code SoftDeleteMedicoResponse} la confirmación de la baja
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el médico no existe
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el médico tiene turnos vivos
     */
    @Transactional
    public SoftDeleteMedicoResponse softDeleteMedico(UUID id) {

        log.info("Baja de médico iniciada: id={}", id);

        //Buscar el médico activo
        Medico medicoExistente = medicoDomainService.findMedicoActivoById(id);

        //Validar que no tenga turnos vivos
        turnoDomainService.validateSinTurnosVivosDeMedico(id);

        //Dar de baja
        medicoDomainService.softDeleteMedico(medicoExistente, "Baja de médico");

        //Desactivar usuario de acceso
        gestionUsuarioPort.desactivarUsuarioDeMedico(id);

        //Devolver response mapeado
        SoftDeleteMedicoResponse softDeleteMedicoResponse = medicoMapper.toSoftDeleteResponse(medicoExistente);
        return softDeleteMedicoResponse;

    }

    //endregion

}
