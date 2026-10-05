package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.AdminApp;
import com.accesmed.backend.Records.Admin.Request.CreateAdminRequest;
import com.accesmed.backend.Records.Admin.Request.UpdateAdminRequest;
import com.accesmed.backend.Records.Admin.Response.CreateAdminResponse;
import com.accesmed.backend.Records.Admin.Response.GetAdminResponse;
import com.accesmed.backend.Records.Admin.Response.UpdateAdminResponse;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.AdminQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Admin.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code AdminApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Admin")
public class AdminController {

    //region ========== Dependencias o inyecciones ==========

    private final AdminApp adminApp;
    private final AdminQueryService adminQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un admin nuevo. La creación es atómica: también crea el usuario de acceso.
     *
     * @param createAdminRequest {@code CreateAdminRequest} datos del admin
     * @return {@code ResponseEntity<CreateAdminResponse>} el admin creado (HTTP 201)
     */
    @PostMapping("/Admin")
    @PreAuthorize("hasAuthority('USER_ALTA')")
    public ResponseEntity<CreateAdminResponse> createAdmin(
            @Valid @RequestBody CreateAdminRequest createAdminRequest) {

        log.info("Solicitud recibida: crear admin email={}", createAdminRequest.email());

        CreateAdminResponse createAdminResponse = adminApp.createAdmin(createAdminRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createAdminResponse);

    }

    /**
     * Actualiza un admin existente.
     *
     * @param id {@code UUID} identificador del admin
     * @param updateAdminRequest {@code UpdateAdminRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateAdminResponse>} el admin actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide
     *         con el del body
     */
    @PutMapping("/Admin/{id}")
    @PreAuthorize("hasAuthority('USER_MODIFICAR')")
    public ResponseEntity<UpdateAdminResponse> updateAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminRequest updateAdminRequest) {

        log.info("Solicitud recibida: actualizar admin id={}", id);

        //Verificar que el id de la ruta coincida con el del body.
        if (!id.equals(updateAdminRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateAdminRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        //Invocar caso de uso.
        UpdateAdminResponse updateAdminResponse = adminApp.updateAdmin(id, updateAdminRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(updateAdminResponse);

    }

    /**
     * Da de baja un admin.
     *
     * @param id {@code UUID} identificador del admin
     * @return {@code ResponseEntity} respuesta sin contenido (HTTP 204)
     */
    @DeleteMapping("/Admin/{id}")
    @PreAuthorize("hasAuthority('USER_BAJA')")
    public ResponseEntity<Void> deleteAdmin(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja admin id={}", id);

        adminApp.deleteAdmin(id);

        return ResponseEntity.noContent().build();

    }

    /**
     * Obtiene un admin por su identificador.
     *
     * @param id {@code UUID} identificador del admin
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<GetAdminResponse>} el admin solicitado (HTTP 200)
     */
    @GetMapping("/Admin/{id}")
    @PreAuthorize("hasAuthority('USER_CONSULTAR')")
    public ResponseEntity<GetAdminResponse> getAdminById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: obtener admin id={}", id);

        GetAdminResponse getAdminResponse = adminQueryService.findAdminById(id, usuarioDetails);

        return ResponseEntity.ok(getAdminResponse);

    }

    //endregion

}
