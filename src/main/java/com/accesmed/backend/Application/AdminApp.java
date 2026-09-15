package com.accesmed.backend.Application;

import com.accesmed.backend.Application.Ports.GestionUsuarioPort;
import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Records.Admin.Request.CreateAdminRequest;
import com.accesmed.backend.Records.Admin.Request.UpdateAdminRequest;
import com.accesmed.backend.Records.Admin.Response.CreateAdminResponse;
import com.accesmed.backend.Records.Admin.Response.UpdateAdminResponse;
import com.accesmed.backend.Services.DomainServices.AdminDomainService;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Mappers.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de Admin. Orquesta el flujo completo de los endpoints (creación atómica
 * con usuario, actualización, baja lógica) validando reglas de negocio y coordinando
 * los services de {@code Admin} y el puerto {@code GestionUsuarioPort}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminApp {

    //region ========== Dependencias ==========

    private final AdminDomainService adminDomainService;
    private final AdminMapper adminMapper;
    private final GestionUsuarioPort gestionUsuarioPort;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un admin nuevo en una única transacción atómica junto con su usuario de acceso.
     * El email del admin se usa como email de login del usuario.
     *
     * @param createAdminRequest {@code CreateAdminRequest} datos del admin
     * @return {@code CreateAdminResponse} el admin creado
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException
     *         {@code ReglaNegocioException} si DNI o email ya existen
     */
    @Transactional
    public CreateAdminResponse createAdmin(CreateAdminRequest createAdminRequest) {

        log.info("Creación de admin iniciada: nombre={}, apellido={}, dni={}, email={}",
                createAdminRequest.nombre(), createAdminRequest.apellido(),
                createAdminRequest.dni(), createAdminRequest.email());

        //Validar unicidad de DNI y email
        adminDomainService.validateDniIsUnique(createAdminRequest.dni());
        adminDomainService.validateEmailIsUnique(createAdminRequest.email());

        //Mapear y guardar el admin
        Admin adminNuevo = adminMapper.toEntity(createAdminRequest);
        Admin adminGuardado = adminDomainService.saveAdmin(adminNuevo);

        //Crear el usuario de acceso en la MISMA transacción
        gestionUsuarioPort.asignarUsuarioAAdmin(adminGuardado.getId(), adminGuardado.getEmail());

        //Devolver response mapeado
        CreateAdminResponse createAdminResponse = adminMapper.toCreateResponse(adminGuardado);
        return createAdminResponse;

    }

    /**
     * Actualiza un admin existente.
     *
     * @param id {@code UUID} identificador del admin (ya validado contra la ruta en el Controller)
     * @param updateAdminRequest {@code UpdateAdminRequest} datos a actualizar, incluyendo el id
     * @return {@code UpdateAdminResponse} el admin actualizado
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide
     *         con el del body
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el admin no existe
     */
    @Transactional
    public UpdateAdminResponse updateAdmin(UUID id, UpdateAdminRequest updateAdminRequest) {

        log.info("Actualización de admin iniciada: id={}", id);

        //Validar que el id de la ruta coincida con el del body
        if (!id.equals(updateAdminRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateAdminRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Buscar el admin activo
        Admin adminExistente = adminDomainService.findAdminActivoById(id);

        //Aplicar cambios y guardar
        adminMapper.update(adminExistente, updateAdminRequest);
        Admin adminActualizado = adminDomainService.saveAdmin(adminExistente);

        //Devolver response mapeado
        UpdateAdminResponse updateAdminResponse = adminMapper.toUpdateResponse(adminActualizado);
        return updateAdminResponse;

    }

    /**
     * Da de baja un admin (baja lógica) y desactiva su usuario de acceso.
     *
     * @param id {@code UUID} identificador del admin
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException
     *         {@code RecursoNoEncontradoException} si el admin no existe
     */
    @Transactional
    public void deleteAdmin(UUID id) {

        log.info("Baja de admin iniciada: id={}", id);

        //Buscar el admin activo
        Admin adminExistente = adminDomainService.findAdminActivoById(id);

        //Dar de baja el admin
        adminDomainService.softDeleteAdmin(adminExistente, "Baja de administrador.");

        //Desactivar el usuario de acceso
        gestionUsuarioPort.desactivarUsuarioDeAdmin(id);

        log.info("Admin dado de baja: id={}", id);

    }

    //endregion

}
