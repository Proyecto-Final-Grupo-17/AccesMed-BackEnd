package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.Ports.GestionUsuarioPort;
import com.accesmed.backend.Records.Usuario.Criteria.UsuarioCriteria;
import com.accesmed.backend.Records.Usuario.Request.AsignarUsuarioRequest;
import com.accesmed.backend.Records.Usuario.Request.IniciarCambioMailRequest;
import com.accesmed.backend.Records.Usuario.Response.GetUsuarioResponse;
import com.accesmed.backend.Records.Usuario.Response.ListUsuarioResponse;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.UsuarioQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Asigna o reasigna el usuario de acceso de un médico o un admin. Cubre tanto el alta
 * inicial (todavía no tenía usuario) como el reemplazo tras una cuenta comprometida
 * (se da de baja el anterior y se crea uno nuevo). Delega directo en
 * {@link GestionUsuarioPort} — el flujo completo (datos + token de activación + mail)
 * vive en la implementación de {@code Security}.
 */
@Slf4j
@RestController
@RequestMapping("/accesmed-api/Usuario")
@RequiredArgsConstructor
public class UsuarioController {

    //region ========== Dependencias o inyecciones ==========

    private final GestionUsuarioPort gestionUsuarioPort;
    private final UsuarioQueryService usuarioQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna o reasigna el usuario de acceso de un médico.
     *
     * @param id {@code UUID} id del médico, tomado de la ruta
     * @param asignarUsuarioRequest {@code AsignarUsuarioRequest} mail de login
     * @return {@code ResponseEntity<Void>} sin contenido (204)
     */
    @PostMapping("/AsignarMedico/{id}")
    @PreAuthorize("hasAuthority('USER_ALTA')")
    public ResponseEntity<Void> asignarUsuarioAMedico(@PathVariable UUID id,
            @Valid @RequestBody AsignarUsuarioRequest asignarUsuarioRequest) {

        log.info("Solicitud recibida: asignar usuario a médico id={}", id);

        gestionUsuarioPort.asignarUsuarioAMedico(id, asignarUsuarioRequest.mail());

        return ResponseEntity.noContent().build();

    }

    /**
     * Asigna o reasigna el usuario de acceso de un admin.
     *
     * @param id {@code UUID} id del admin, tomado de la ruta
     * @param asignarUsuarioRequest {@code AsignarUsuarioRequest} mail de login
     * @return {@code ResponseEntity<Void>} sin contenido (204)
     */
    @PostMapping("/AsignarAdmin/{id}")
    @PreAuthorize("hasAuthority('USER_ALTA')")
    public ResponseEntity<Void> asignarUsuarioAAdmin(@PathVariable UUID id,
            @Valid @RequestBody AsignarUsuarioRequest asignarUsuarioRequest) {

        log.info("Solicitud recibida: asignar usuario a admin id={}", id);

        gestionUsuarioPort.asignarUsuarioAAdmin(id, asignarUsuarioRequest.mail());

        return ResponseEntity.noContent().build();

    }

    /**
     * Lista usuarios según el criteria de filtrado dinámico proporcionado, incluyendo
     * activos e inactivos según el {@code estado} pedido.
     *
     * @param usuarioCriteria {@code UsuarioCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListUsuarioResponse>>} página de usuarios (HTTP 200)
     */
    @PreAuthorize("hasAuthority('USER_CONSULTAR')")
    @GetMapping("/Usuario")
    public ResponseEntity<PageResponse<ListUsuarioResponse>> findUsuarios(
            @ParameterObject UsuarioCriteria usuarioCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "mail") Pageable pageable) {

        log.info("Solicitud recibida: listar usuarios criteria={} page={}", usuarioCriteria, pageable);

        PageResponse<ListUsuarioResponse> pageResponse = usuarioQueryService.findUsuarios(usuarioCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Obtiene el detalle de un usuario por identificador, esté activo o dado de baja.
     *
     * @param id {@code UUID} identificador del usuario
     * @return {@code ResponseEntity<GetUsuarioResponse>} el usuario encontrado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('USER_CONSULTAR')")
    @GetMapping("/Usuario/{id}")
    public ResponseEntity<GetUsuarioResponse> findUsuarioById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener usuario id={}", id);

        GetUsuarioResponse getUsuarioResponse = usuarioQueryService.findUsuarioById(id);

        return ResponseEntity.ok(getUsuarioResponse);

    }

    /**
     * Da de baja un usuario directamente — no implica dar de baja al médico/admin
     * vinculado, solo revoca su acceso.
     *
     * @param id {@code UUID} identificador del usuario
     * @param motivo {@code String} motivo de la baja, opcional (si no se manda, se usa un
     *         motivo por default)
     * @return {@code ResponseEntity<Void>} sin contenido (HTTP 204)
     */
    @PreAuthorize("hasAuthority('USER_BAJA')")
    @DeleteMapping("/Usuario/{id}")
    public ResponseEntity<Void> softDeleteUsuario(@PathVariable UUID id,
            @RequestParam(required = false) String motivo) {

        log.info("Solicitud recibida: dar de baja usuario id={}", id);

        gestionUsuarioPort.desactivarUsuarioDirecto(id, motivo);

        return ResponseEntity.noContent().build();

    }

    /**
     * Dispara el mail de restablecimiento de contraseña de un usuario, a nombre del
     * SuperAdmin (mismo mecanismo que el autoservicio en {@code /Auth/CambiarContrasena}).
     *
     * @param id {@code UUID} identificador del usuario
     * @return {@code ResponseEntity<Void>} sin contenido (HTTP 204)
     */
    @PreAuthorize("hasAuthority('USER_MODIFICAR')")
    @PostMapping("/Usuario/{id}/RestablecerContrasena")
    public ResponseEntity<Void> dispararResetContrasena(@PathVariable UUID id) {

        log.info("Solicitud recibida: disparar reset de contraseña de usuario id={}", id);

        gestionUsuarioPort.dispararResetContrasena(id);

        return ResponseEntity.noContent().build();

    }

    /**
     * Inicia el cambio de mail de un usuario, a nombre del SuperAdmin. El mail actual
     * sigue vigente hasta que se confirma el link mandado al mail nuevo (ver
     * {@code POST /Auth/ConfirmarCambioMail}).
     *
     * @param id {@code UUID} identificador del usuario
     * @param iniciarCambioMailRequest {@code IniciarCambioMailRequest} mail nuevo
     * @return {@code ResponseEntity<Void>} sin contenido (HTTP 204)
     */
    @PreAuthorize("hasAuthority('USER_MODIFICAR')")
    @PostMapping("/Usuario/{id}/CambiarMail")
    public ResponseEntity<Void> iniciarCambioMail(@PathVariable UUID id,
            @Valid @RequestBody IniciarCambioMailRequest iniciarCambioMailRequest) {

        log.info("Solicitud recibida: iniciar cambio de mail de usuario id={}", id);

        gestionUsuarioPort.iniciarCambioMail(id, iniciarCambioMailRequest.mailNuevo());

        return ResponseEntity.noContent().build();

    }

    //endregion

}
