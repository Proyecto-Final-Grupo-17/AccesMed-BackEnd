package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.RolApp;
import com.accesmed.backend.Records.Rol.Request.AsignarRolRequest;
import com.accesmed.backend.Records.Rol.Request.CreateRolRequest;
import com.accesmed.backend.Records.Rol.Request.UpdateRolRequest;
import com.accesmed.backend.Records.Rol.Response.CreateRolResponse;
import com.accesmed.backend.Records.Rol.Response.GetRolResponse;
import com.accesmed.backend.Records.Rol.Response.UpdateRolResponse;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.RolQueryService;
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
 * Controlador REST para los endpoints de Rol.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code RolApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Rol")
public class RolController {

    //region ========== Dependencias o inyecciones ==========

    private final RolApp rolApp;
    private final RolQueryService rolQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un rol nuevo.
     *
     * @param createRolRequest {@code CreateRolRequest} datos del rol
     * @return {@code ResponseEntity<CreateRolResponse>} el rol creado (HTTP 201)
     */
    @PostMapping("/Rol")
    @PreAuthorize("hasAuthority('AUTZ_ROL_ALTA')")
    public ResponseEntity<CreateRolResponse> createRol(
            @Valid @RequestBody CreateRolRequest createRolRequest) {

        log.info("Solicitud recibida: crear rol nombre={}", createRolRequest.nombre());

        CreateRolResponse createRolResponse = rolApp.createRol(createRolRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createRolResponse);

    }

    /**
     * Actualiza un rol existente.
     *
     * @param id {@code UUID} identificador del rol
     * @param updateRolRequest {@code UpdateRolRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateRolResponse>} el rol actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide
     *         con el del body
     */
    @PutMapping("/Rol/{id}")
    @PreAuthorize("hasAuthority('AUTZ_ROL_MODIFICAR')")
    public ResponseEntity<UpdateRolResponse> updateRol(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRolRequest updateRolRequest) {

        log.info("Solicitud recibida: actualizar rol id={}", id);

        //Verificar que el id de la ruta coincida con el del body.
        if (!id.equals(updateRolRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateRolRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso.
        UpdateRolResponse updateRolResponse = rolApp.updateRol(id, updateRolRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(updateRolResponse);

    }

    /**
     * Da de baja un rol.
     *
     * @param id {@code UUID} identificador del rol
     * @return {@code ResponseEntity} respuesta sin contenido (HTTP 204)
     */
    @DeleteMapping("/Rol/{id}")
    @PreAuthorize("hasAuthority('AUTZ_ROL_BAJA')")
    public ResponseEntity<Void> deleteRol(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja rol id={}", id);

        rolApp.deleteRol(id);

        return ResponseEntity.noContent().build();

    }

    /**
     * Obtiene un rol por su identificador.
     *
     * @param id {@code UUID} identificador del rol
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<GetRolResponse>} el rol solicitado (HTTP 200)
     */
    @GetMapping("/Rol/{id}")
    @PreAuthorize("hasAuthority('AUTZ_ROL_CONSULTAR')")
    public ResponseEntity<GetRolResponse> getRolById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: obtener rol id={}", id);

        GetRolResponse getRolResponse = rolQueryService.findRolById(id, usuarioDetails);

        return ResponseEntity.ok(getRolResponse);

    }

    /**
     * Lista todos los roles activos.
     *
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<List<GetRolResponse>>} listado de roles (HTTP 200)
     */
    @GetMapping("/Rol")
    @PreAuthorize("hasAuthority('AUTZ_ROL_CONSULTAR')")
    public ResponseEntity<List<GetRolResponse>> listRoles(
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar todos los roles");

        List<GetRolResponse> getRolResponses = rolQueryService.findRoles(usuarioDetails);

        return ResponseEntity.ok(getRolResponses);

    }

    /**
     * Asigna un rol a un usuario.
     *
     * @param rolId {@code UUID} identificador del rol
     * @param asignarRolRequest {@code AsignarRolRequest} datos con el id del usuario
     * @return {@code ResponseEntity} respuesta sin contenido (HTTP 204)
     */
    @PostMapping("/Rol/{rolId}/AsignarUsuario")
    @PreAuthorize("hasAuthority('AUTZ_ROL_ASIGNAR')")
    public ResponseEntity<Void> asignarRol(
            @PathVariable UUID rolId,
            @Valid @RequestBody AsignarRolRequest asignarRolRequest) {

        log.info("Solicitud recibida: asignar rol rolId={}, usuarioId={}", rolId, asignarRolRequest.usuarioId());

        rolApp.asignarRol(rolId, asignarRolRequest);

        return ResponseEntity.noContent().build();

    }

    /**
     * Revoca un rol de un usuario.
     *
     * @param rolId {@code UUID} identificador del rol
     * @param usuarioId {@code UUID} identificador del usuario
     * @return {@code ResponseEntity} respuesta sin contenido (HTTP 204)
     */
    @DeleteMapping("/Rol/{rolId}/RevocarUsuario/{usuarioId}")
    @PreAuthorize("hasAuthority('AUTZ_ROL_ASIGNAR')")
    public ResponseEntity<Void> revocarRol(
            @PathVariable UUID rolId,
            @PathVariable UUID usuarioId) {

        log.info("Solicitud recibida: revocar rol rolId={}, usuarioId={}", rolId, usuarioId);

        rolApp.revocarRol(rolId, usuarioId);

        return ResponseEntity.noContent().build();

    }

    //endregion

}
