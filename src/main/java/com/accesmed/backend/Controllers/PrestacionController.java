package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.PrestacionApp;
import com.accesmed.backend.Records.Prestacion.Request.CreatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdatePrestacionNoHabilitadaRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdateToleranciasPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Response.CreatePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.EnablePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.GetPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.SoftDeletePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionNoHabilitadaResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdateToleranciasPrestacionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Prestación.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code PrestacionApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Prestacion")
public class PrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionApp prestacionApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una prestación nueva.
     *
     * @param createPrestacionRequest {@code CreatePrestacionRequest} datos de la prestación
     * @return {@code ResponseEntity<CreatePrestacionResponse>} la prestación creada (HTTP 201)
     */
    @PostMapping("/Prestacion")
    public ResponseEntity<CreatePrestacionResponse> createPrestacion(
            @Valid @RequestBody CreatePrestacionRequest createPrestacionRequest) {

        log.info("Solicitud recibida: crear prestación código={}", createPrestacionRequest.codigo());

        CreatePrestacionResponse response = prestacionApp.createPrestacion(createPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * Actualiza los datos generales (nombre, especialidad) de una prestación en borrador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @param updatePrestacionNoHabilitadaRequest {@code UpdatePrestacionNoHabilitadaRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdatePrestacionNoHabilitadaResponse>} la prestación actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/Prestacion/{id}")
    public ResponseEntity<UpdatePrestacionNoHabilitadaResponse> updatePrestacionNoHabilitada(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePrestacionNoHabilitadaRequest updatePrestacionNoHabilitadaRequest) {

        log.info("Solicitud recibida: actualizar datos generales de prestación id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updatePrestacionNoHabilitadaRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePrestacionNoHabilitadaRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UpdatePrestacionNoHabilitadaResponse response = prestacionApp
                .updatePrestacionNoHabilitada(id, updatePrestacionNoHabilitadaRequest);

        return ResponseEntity.ok(response);

    }

    /**
     * Actualiza las duraciones y tolerancias de una prestación.
     *
     * @param id {@code UUID} identificador de la prestación
     * @param updateToleranciasPrestacionRequest {@code UpdateToleranciasPrestacionRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateToleranciasPrestacionResponse>} la prestación actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/Prestacion/Tolerancias/{id}")
    public ResponseEntity<UpdateToleranciasPrestacionResponse> updateToleranciasPrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateToleranciasPrestacionRequest updateToleranciasPrestacionRequest) {

        log.info("Solicitud recibida: actualizar tolerancias de prestación id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateToleranciasPrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateToleranciasPrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UpdateToleranciasPrestacionResponse response = prestacionApp
                .updateToleranciasPrestacion(id, updateToleranciasPrestacionRequest);

        return ResponseEntity.ok(response);

    }

    /**
     * Habilita una prestación (cambio irreversible de estado).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code ResponseEntity<EnablePrestacionResponse>} la prestación habilitada (HTTP 200)
     */
    @PatchMapping("/Prestacion/{id}/Habilitacion")
    public ResponseEntity<EnablePrestacionResponse> habilitarPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: habilitar prestación id={}", id);

        EnablePrestacionResponse response = prestacionApp.habilitarPrestacion(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Da de baja una prestación (baja lógica).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code ResponseEntity<SoftDeletePrestacionResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/Prestacion/{id}")
    public ResponseEntity<SoftDeletePrestacionResponse> softDeletePrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja prestación id={}", id);

        SoftDeletePrestacionResponse response = prestacionApp.softDeletePrestacion(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Obtiene una prestación por su identificador.
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code ResponseEntity<GetPrestacionResponse>} la prestación encontrada (HTTP 200)
     */
    @GetMapping("/Prestacion/{id}")
    public ResponseEntity<GetPrestacionResponse> findPrestacionById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener prestación id={}", id);

        GetPrestacionResponse response = prestacionApp.findPrestacionById(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Lista prestaciones según los filtros proporcionados.
     *
     * @param especialidadId {@code UUID} opcional, para filtrar por especialidad
     * @param habilitadas {@code Boolean} opcional, para filtrar por estado
     * @return {@code ResponseEntity<List<ListPrestacionResponse>>} lista de prestaciones (HTTP 200)
     */
    @GetMapping("/Prestacion")
    public ResponseEntity<List<ListPrestacionResponse>> findPrestaciones(
            @RequestParam(required = false) UUID especialidadId,
            @RequestParam(required = false) Boolean habilitadas) {

        log.info("Solicitud recibida: listar prestaciones especialidadId={} habilitadas={}", especialidadId, habilitadas);

        List<ListPrestacionResponse> response = prestacionApp.findPrestaciones(especialidadId, habilitadas);

        return ResponseEntity.ok(response);

    }

    //endregion

}
