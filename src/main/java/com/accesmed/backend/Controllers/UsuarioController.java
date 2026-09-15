package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.Ports.GestionUsuarioPort;
import com.accesmed.backend.Records.Usuario.Request.AsignarUsuarioRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    //endregion

}
