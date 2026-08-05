package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.EspecialidadApp;
import com.accesmed.backend.Records.Especialidad.Request.CreateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Request.UpdateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Response.CreateEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.GetEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.ListEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.SoftDeleteEspecialidadResponse;
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
 * Controlador REST para los endpoints de Especialidad.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code EspecialidadApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Especialidad")
public class EspecialidadController {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadApp especialidadApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una especialidad nueva.
     *
     * @param createEspecialidadRequest {@code CreateEspecialidadRequest} datos de la especialidad
     * @return {@code ResponseEntity<CreateEspecialidadResponse>} la especialidad creada (HTTP 201)
     */
    @PostMapping("/Especialidad")
    public ResponseEntity<CreateEspecialidadResponse> createEspecialidad(
            @Valid @RequestBody CreateEspecialidadRequest createEspecialidadRequest) {

        log.info("Solicitud recibida: crear especialidad código={}", createEspecialidadRequest.codigo());

        CreateEspecialidadResponse response = especialidadApp.createEspecialidad(createEspecialidadRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * Actualiza una especialidad existente.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @param updateEspecialidadRequest {@code UpdateEspecialidadRequest} datos a actualizar
     * @return {@code ResponseEntity<GetEspecialidadResponse>} la especialidad actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/Especialidad/{id}")
    public ResponseEntity<GetEspecialidadResponse> updateEspecialidad(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEspecialidadRequest updateEspecialidadRequest) {

        log.info("Solicitud recibida: actualizar especialidad id={}", id);

        if (!id.equals(updateEspecialidadRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateEspecialidadRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        GetEspecialidadResponse response = especialidadApp.updateEspecialidad(id, updateEspecialidadRequest);

        return ResponseEntity.ok(response);

    }

    /**
     * Obtiene una especialidad por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code ResponseEntity<GetEspecialidadResponse>} la especialidad encontrada (HTTP 200)
     */
    @GetMapping("/Especialidad/{id}")
    public ResponseEntity<GetEspecialidadResponse> findEspecialidadById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener especialidad id={}", id);

        GetEspecialidadResponse response = especialidadApp.findEspecialidadById(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Lista todas las especialidades activas.
     *
     * @return {@code ResponseEntity<List<ListEspecialidadResponse>>} lista de especialidades (HTTP 200)
     */
    @GetMapping("/Especialidad")
    public ResponseEntity<List<ListEspecialidadResponse>> findEspecialidades() {

        log.info("Solicitud recibida: listar especialidades");

        List<ListEspecialidadResponse> response = especialidadApp.findEspecialidades();

        return ResponseEntity.ok(response);

    }

    /**
     * Da de baja una especialidad (baja lógica restrictiva).
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code ResponseEntity<SoftDeleteEspecialidadResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/Especialidad/{id}")
    public ResponseEntity<SoftDeleteEspecialidadResponse> softDeleteEspecialidad(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja especialidad id={}", id);

        SoftDeleteEspecialidadResponse response = especialidadApp.softDeleteEspecialidad(id);

        return ResponseEntity.ok(response);

    }

    //endregion

}
