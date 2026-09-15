package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Records.Rol.Request.AsignarRolRequest;
import com.accesmed.backend.Records.Rol.Request.CreateRolRequest;
import com.accesmed.backend.Records.Rol.Request.UpdateRolRequest;
import com.accesmed.backend.Records.Rol.Response.CreateRolResponse;
import com.accesmed.backend.Records.Rol.Response.UpdateRolResponse;
import com.accesmed.backend.Repositories.UsuarioRepository;
import com.accesmed.backend.Services.DomainServices.RolDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.RolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Rol. Orquesta el flujo completo de los endpoints (creación, actualización,
 * baja lógica, asignación y revocación de roles a usuarios) validando reglas de negocio
 * y coordinando los services de {@code Rol} y {@code UsuarioRol}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolApp {

    //region ========== Dependencias ==========

    private final RolDomainService rolDomainService;
    private final UsuarioRolDomainService usuarioRolDomainService;
    private final RolMapper rolMapper;
    private final UsuarioRepository usuarioRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un rol nuevo con su conjunto de permisos.
     *
     * @param createRolRequest {@code CreateRolRequest} datos del rol
     * @return {@code CreateRolResponse} el rol creado
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el nombre ya existe
     */
    @Transactional
    public CreateRolResponse createRol(CreateRolRequest createRolRequest) {

        log.info("Creación de rol iniciada: nombre={}", createRolRequest.nombre());

        //Validar unicidad del nombre
        rolDomainService.validateNombreRolIsUnique(createRolRequest.nombre());

        //Mapear y guardar
        Rol rolNuevo = rolMapper.toEntity(createRolRequest);
        rolNuevo.setEsSistema(false);
        Rol rolGuardado = rolDomainService.saveRol(rolNuevo);

        //Devolver response mapeado
        CreateRolResponse createRolResponse = rolMapper.toCreateResponse(rolGuardado);
        return createRolResponse;

    }

    /**
     * Actualiza un rol existente.
     *
     * @param id {@code UUID} identificador del rol (ya validado contra la ruta en el Controller)
     * @param updateRolRequest {@code UpdateRolRequest} datos a actualizar, incluyendo el id
     * @return {@code UpdateRolResponse} el rol actualizado
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide
     *         con el del body
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el rol no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el rol es de sistema o el nombre es duplicado
     */
    @Transactional
    public UpdateRolResponse updateRol(UUID id, UpdateRolRequest updateRolRequest) {

        log.info("Actualización de rol iniciada: id={}", id);

        //Validar que el id de la ruta coincida con el del body
        if (!id.equals(updateRolRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateRolRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Buscar el rol activo
        Rol rolExistente = rolDomainService.findRolActivoById(id);

        //Validar que no sea un rol de sistema
        rolDomainService.validateRolEditable(rolExistente);

        //Validar unicidad del nombre
        rolDomainService.validateNombreRolIsUnique(updateRolRequest.nombre());

        //Aplicar cambios y guardar
        rolMapper.update(rolExistente, updateRolRequest);
        Rol rolActualizado = rolDomainService.saveRol(rolExistente);

        //Devolver response mapeado
        UpdateRolResponse updateRolResponse = rolMapper.toUpdateResponse(rolActualizado);
        return updateRolResponse;

    }

    /**
     * Da de baja un rol (baja lógica).
     *
     * @param id {@code UUID} identificador del rol
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el rol no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si el rol es de sistema
     */
    @Transactional
    public void deleteRol(UUID id) {

        log.info("Baja de rol iniciada: id={}", id);

        //Buscar el rol activo
        Rol rolExistente = rolDomainService.findRolActivoById(id);

        //Validar que no sea un rol de sistema
        rolDomainService.validateRolEditable(rolExistente);

        //Dar de baja
        rolExistente.setDeletedAt(Instant.now());
        rolDomainService.saveRol(rolExistente);

        log.info("Rol dado de baja: id={}", id);

    }

    /**
     * Asigna un rol a un usuario en una transacción atómica.
     *
     * @param rolId {@code UUID} identificador del rol
     * @param asignarRolRequest {@code AsignarRolRequest} datos con el id del usuario
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si el rol o
     *         el usuario no existen
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si las validaciones de negocio fallan
     */
    @Transactional
    public void asignarRol(UUID rolId, AsignarRolRequest asignarRolRequest) {

        log.info("Asignación de rol iniciada: rolId={}, usuarioId={}", rolId, asignarRolRequest.usuarioId());

        //Buscar el usuario
        Usuario usuarioExistente = usuarioRepository.findByIdAndDeletedAtIsNull(asignarRolRequest.usuarioId())
                .orElseThrow(() -> {
                    log.warn("No se encontró el usuario activo: id={}", asignarRolRequest.usuarioId());
                    return new RecursoNoEncontradoException(getClass(), "USUARIO_NO_ENCONTRADO",
                            "No existe un usuario activo con el id proporcionado.");
                });

        //Buscar el rol
        Rol rolExistente = rolDomainService.findRolActivoById(rolId);

        //Validar las precondiciones de negocio
        usuarioRolDomainService.validateAsignacionRol(usuarioExistente, rolExistente);

        //Crear la asignación
        UsuarioRol usuarioRolNuevo = new UsuarioRol();
        usuarioRolNuevo.setUsuario(usuarioExistente);
        usuarioRolNuevo.setRol(rolExistente);
        usuarioRolNuevo.setFechaInicioVigencia(ZonedDateTime.now());

        usuarioRolDomainService.saveUsuarioRol(usuarioRolNuevo);

        log.info("Rol asignado: rolId={}, usuarioId={}", rolId, asignarRolRequest.usuarioId());

    }

    /**
     * Revoca un rol de un usuario.
     *
     * @param rolId {@code UUID} identificador del rol
     * @param usuarioId {@code UUID} identificador del usuario
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la
     *         asignación no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si se intenta revocar SuperAdmin
     */
    @Transactional
    public void revocarRol(UUID rolId, UUID usuarioId) {

        log.info("Revocación de rol iniciada: rolId={}, usuarioId={}", rolId, usuarioId);

        //Buscar la asignación vigente
        List<UsuarioRol> asignacionesVigentes = usuarioRolDomainService.findVigentesByUsuarioId(usuarioId);

        UsuarioRol asignacionARevoca = asignacionesVigentes.stream()
                .filter(ur -> ur.getRol().getId().equals(rolId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No se encontró la asignación de rol: rolId={}, usuarioId={}", rolId, usuarioId);
                    return new RecursoNoEncontradoException(getClass(), "ASIGNACION_ROL_NO_ENCONTRADA",
                            "No existe una asignación vigente de este rol para el usuario.");
                });

        //Revocar
        usuarioRolDomainService.revocarUsuarioRol(asignacionARevoca);

        log.info("Rol revocado: rolId={}, usuarioId={}", rolId, usuarioId);

    }

    //endregion

}
