package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.TipoIndicacionPrestacionApp;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.UpdateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.CreateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.UpdateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.CreateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.ListTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.GetTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.SoftDeleteTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Controlador REST para los endpoints de Tipo de Indicación de Prestación.
 * Recibe requests, delega en el caso de uso {@code TipoIndicacionPrestacionApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/TipoIndicacionPrestacion")
public class TipoIndicacionPrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionApp tipoIndicacionPrestacionApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un tipo de indicación de prestación nuevo.
     *
     * @param createTipoIndicacionPrestacionRequest {@code CreateTipoIndicacionPrestacionRequest} datos del tipo
     * @return {@code ResponseEntity<CreateTipoIndicacionPrestacionResponse>} el tipo creado (HTTP 201)
     */
    @PostMapping("/TipoIndicacionPrestacion")
    public ResponseEntity<CreateTipoIndicacionPrestacionResponse> createTipoIndicacionPrestacion(
            @Valid @RequestBody CreateTipoIndicacionPrestacionRequest createTipoIndicacionPrestacionRequest) {

        log.info("Solicitud recibida: crear tipo de indicación código={}", createTipoIndicacionPrestacionRequest.codigo());

        CreateTipoIndicacionPrestacionResponse response = tipoIndicacionPrestacionApp
                .createTipoIndicacionPrestacion(createTipoIndicacionPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * Actualiza un tipo de indicación de prestación existente.
     *
     * @param id {@code UUID} identificador del tipo
     * @param updateTipoIndicacionPrestacionRequest {@code UpdateTipoIndicacionPrestacionRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateTipoIndicacionPrestacionResponse>} el tipo actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PutMapping("/TipoIndicacionPrestacion/{id}")
    public ResponseEntity<UpdateTipoIndicacionPrestacionResponse> updateTipoIndicacionPrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTipoIndicacionPrestacionRequest updateTipoIndicacionPrestacionRequest) {

        log.info("Solicitud recibida: actualizar tipo de indicación id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateTipoIndicacionPrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateTipoIndicacionPrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UpdateTipoIndicacionPrestacionResponse response = tipoIndicacionPrestacionApp
                .updateTipoIndicacionPrestacion(id, updateTipoIndicacionPrestacionRequest);

        return ResponseEntity.ok(response);

    }

    /**
     * Da de baja un tipo de indicación de prestación (baja lógica y restrictiva).
     *
     * @param id {@code UUID} identificador del tipo
     * @return {@code ResponseEntity<SoftDeleteTipoIndicacionPrestacionResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/TipoIndicacionPrestacion/{id}")
    public ResponseEntity<SoftDeleteTipoIndicacionPrestacionResponse> softDeleteTipoIndicacionPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja tipo de indicación id={}", id);

        SoftDeleteTipoIndicacionPrestacionResponse response = tipoIndicacionPrestacionApp.softDeleteTipoIndicacionPrestacion(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Obtiene un tipo de indicación de prestación por su identificador.
     *
     * @param id {@code UUID} identificador del tipo
     * @return {@code ResponseEntity<GetTipoIndicacionPrestacionResponse>} el tipo encontrado (HTTP 200)
     */
    @GetMapping("/TipoIndicacionPrestacion/{id}")
    public ResponseEntity<GetTipoIndicacionPrestacionResponse> findTipoIndicacionPrestacionById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener tipo de indicación id={}", id);

        GetTipoIndicacionPrestacionResponse response = tipoIndicacionPrestacionApp
                .findTipoIndicacionPrestacionById(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Lista todos los tipos de indicación de prestación activos.
     *
     * @return {@code ResponseEntity<List<ListTipoIndicacionPrestacionResponse>>} lista de tipos (HTTP 200)
     */
    @GetMapping("/TipoIndicacionPrestacion")
    public ResponseEntity<List<ListTipoIndicacionPrestacionResponse>> findAllTiposIndicacionPrestacion() {

        log.info("Solicitud recibida: listar todos los tipos de indicación");

        List<ListTipoIndicacionPrestacionResponse> response = tipoIndicacionPrestacionApp
                .findAllTiposIndicacionPrestacion();

        return ResponseEntity.ok(response);

    }

    //endregion

}
