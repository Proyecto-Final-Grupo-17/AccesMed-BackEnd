package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.ObraSocialApp;
import com.accesmed.backend.Records.ObraSocial.Request.CreateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Request.UpdateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Response.CreateObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.ListObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.SoftDeleteObraSocialResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Obra Social.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code ObraSocialApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/ObraSocial")
public class ObraSocialController {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialApp obraSocialApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una obra social nueva junto con sus planes iniciales (alta atómica).
     *
     * @param createObraSocialRequest {@code CreateObraSocialRequest} datos de la obra social y sus planes
     * @return {@code ResponseEntity<CreateObraSocialResponse>} la obra social creada (HTTP 201)
     */
    @PostMapping("/ObraSocial")
    public ResponseEntity<CreateObraSocialResponse> createObraSocial(
            @Valid @RequestBody CreateObraSocialRequest createObraSocialRequest) {

        log.info("Solicitud recibida: crear obra social código={}", createObraSocialRequest.codigo());

        CreateObraSocialResponse response = obraSocialApp.createObraSocial(createObraSocialRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * Actualiza una obra social existente.
     *
     * @param id {@code UUID} identificador de la obra social
     * @param updateObraSocialRequest {@code UpdateObraSocialRequest} datos a actualizar
     * @return {@code ResponseEntity<GetObraSocialResponse>} la obra social actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/ObraSocial/{id}")
    public ResponseEntity<GetObraSocialResponse> updateObraSocial(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateObraSocialRequest updateObraSocialRequest) {

        log.info("Solicitud recibida: actualizar obra social id={}", id);

        if (!id.equals(updateObraSocialRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateObraSocialRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        GetObraSocialResponse response = obraSocialApp.updateObraSocial(id, updateObraSocialRequest);

        return ResponseEntity.ok(response);

    }

    /**
     * Obtiene una obra social por su identificador, con sus planes.
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code ResponseEntity<GetObraSocialResponse>} la obra social encontrada (HTTP 200)
     */
    @GetMapping("/ObraSocial/{id}")
    public ResponseEntity<GetObraSocialResponse> findObraSocialById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener obra social id={}", id);

        GetObraSocialResponse response = obraSocialApp.findObraSocialById(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Lista todas las obras sociales activas.
     *
     * @return {@code ResponseEntity<List<ListObraSocialResponse>>} lista de obras sociales (HTTP 200)
     */
    @GetMapping("/ObraSocial")
    public ResponseEntity<List<ListObraSocialResponse>> findObrasSociales() {

        log.info("Solicitud recibida: listar obras sociales");

        List<ListObraSocialResponse> response = obraSocialApp.findObrasSociales();

        return ResponseEntity.ok(response);

    }

    /**
     * Da de baja una obra social (baja lógica restrictiva por transitividad, con cascada
     * sobre sus planes).
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code ResponseEntity<SoftDeleteObraSocialResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/ObraSocial/{id}")
    public ResponseEntity<SoftDeleteObraSocialResponse> softDeleteObraSocial(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja obra social id={}", id);

        SoftDeleteObraSocialResponse response = obraSocialApp.softDeleteObraSocial(id);

        return ResponseEntity.ok(response);

    }

    //endregion

}
