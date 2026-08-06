package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.IndicacionPrestacionApp;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.CreateIndicacionesPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionesPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.SoftDeleteIndicacionPrestacionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Indicación de Prestación.
 * Recibe requests, delega en el caso de uso {@code IndicacionPrestacionApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/IndicacionPrestacion")
public class IndicacionPrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionApp indicacionPrestacionApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea varias indicaciones de prestación juntas, en una sola operación.
     *
     * @param createIndicacionesPrestacionRequest {@code CreateIndicacionesPrestacionRequest} prestación e indicaciones a crear
     * @return {@code ResponseEntity<CreateIndicacionesPrestacionResponse>} las indicaciones creadas (HTTP 201)
     */
    @PostMapping("/IndicacionPrestacion")
    public ResponseEntity<CreateIndicacionesPrestacionResponse> createIndicacionesPrestacion(
            @Valid @RequestBody CreateIndicacionesPrestacionRequest createIndicacionesPrestacionRequest) {

        log.info("Solicitud recibida: crear indicaciones de prestación prestacionId={}",
                createIndicacionesPrestacionRequest.prestacionId());

        CreateIndicacionesPrestacionResponse response = indicacionPrestacionApp
                .createIndicacionesPrestacion(createIndicacionesPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * Actualiza una indicación de prestación existente.
     *
     * @param id {@code UUID} identificador de la indicación
     * @param updateIndicacionPrestacionRequest {@code UpdateIndicacionPrestacionRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateIndicacionPrestacionResponse>} la indicación actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PutMapping("/IndicacionPrestacion/{id}")
    public ResponseEntity<UpdateIndicacionPrestacionResponse> updateIndicacionPrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIndicacionPrestacionRequest updateIndicacionPrestacionRequest) {

        log.info("Solicitud recibida: actualizar indicación de prestación id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateIndicacionPrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateIndicacionPrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UpdateIndicacionPrestacionResponse response = indicacionPrestacionApp
                .updateIndicacionPrestacion(id, updateIndicacionPrestacionRequest);

        return ResponseEntity.ok(response);

    }

    /**
     * Da de baja una indicación de prestación (baja lógica).
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code ResponseEntity<SoftDeleteIndicacionPrestacionResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/IndicacionPrestacion/{id}")
    public ResponseEntity<SoftDeleteIndicacionPrestacionResponse> softDeleteIndicacionPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja indicación de prestación id={}", id);

        SoftDeleteIndicacionPrestacionResponse response = indicacionPrestacionApp.softDeleteIndicacionPrestacion(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Obtiene una indicación de prestación por su identificador.
     *
     * @param id {@code UUID} identificador de la indicación
     * @return {@code ResponseEntity<GetIndicacionPrestacionResponse>} la indicación encontrada (HTTP 200)
     */
    @GetMapping("/IndicacionPrestacion/{id}")
    public ResponseEntity<GetIndicacionPrestacionResponse> findIndicacionPrestacionById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener indicación de prestación id={}", id);

        GetIndicacionPrestacionResponse response = indicacionPrestacionApp.findIndicacionPrestacionById(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Lista indicaciones de prestación según los filtros proporcionados.
     *
     * @param prestacionId {@code UUID} opcional, para filtrar por prestación
     * @return {@code ResponseEntity<List<ListIndicacionPrestacionResponse>>} lista de indicaciones (HTTP 200)
     */
    @GetMapping("/IndicacionPrestacion")
    public ResponseEntity<List<ListIndicacionPrestacionResponse>> findIndicacionesPrestacion(
            @RequestParam(required = false) UUID prestacionId) {

        log.info("Solicitud recibida: listar indicaciones de prestación prestacionId={}", prestacionId);

        List<ListIndicacionPrestacionResponse> response = indicacionPrestacionApp
                .findIndicacionesPrestacion(prestacionId);

        return ResponseEntity.ok(response);

    }

    //endregion

}
