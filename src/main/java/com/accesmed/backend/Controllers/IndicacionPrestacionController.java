package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.IndicacionPrestacionApp;
import com.accesmed.backend.Records.IndicacionPrestacion.Criteria.IndicacionPrestacionCriteria;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.CreateIndicacionesPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.ScheduleBajaIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Request.UpdateIndicacionPrestacionRequest;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.CreateIndicacionesPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.UpdateIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ListIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.GetIndicacionPrestacionResponse;
import com.accesmed.backend.Records.IndicacionPrestacion.Response.ScheduleBajaIndicacionPrestacionResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.IndicacionPrestacionQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * Controlador REST para los endpoints de Indicación de Prestación.
 * Recibe requests, delega en el caso de uso {@code IndicacionPrestacionApp} (escritura) o
 * {@code IndicacionPrestacionQueryService} (lectura), y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/IndicacionPrestacion")
public class IndicacionPrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final IndicacionPrestacionApp indicacionPrestacionApp;
    private final IndicacionPrestacionQueryService indicacionPrestacionQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea varias indicaciones de prestación juntas, en una sola operación.
     *
     * @param createIndicacionesPrestacionRequest {@code CreateIndicacionesPrestacionRequest} prestación e indicaciones a crear
     * @return {@code ResponseEntity<CreateIndicacionesPrestacionResponse>} las indicaciones creadas (HTTP 201)
     */
    @PreAuthorize("hasAuthority('PREST_ALTA')")
    @PostMapping("/IndicacionPrestacion")
    public ResponseEntity<CreateIndicacionesPrestacionResponse> createIndicacionesPrestacion(
            @Valid @RequestBody CreateIndicacionesPrestacionRequest createIndicacionesPrestacionRequest) {

        log.info("Solicitud recibida: crear indicaciones de prestación prestacionId={}",
                createIndicacionesPrestacionRequest.prestacionId());

        CreateIndicacionesPrestacionResponse createIndicacionesPrestacionResponse = indicacionPrestacionApp
                .createIndicacionesPrestacion(createIndicacionesPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createIndicacionesPrestacionResponse);

    }

    /**
     * Actualiza una indicación de prestación existente.
     *
     * @param id {@code UUID} identificador de la indicación
     * @param updateIndicacionPrestacionRequest {@code UpdateIndicacionPrestacionRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateIndicacionPrestacionResponse>} la indicación actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('PREST_MODIFICAR')")
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

        UpdateIndicacionPrestacionResponse updateIndicacionPrestacionResponse = indicacionPrestacionApp
                .updateIndicacionPrestacion(updateIndicacionPrestacionRequest);

        return ResponseEntity.ok(updateIndicacionPrestacionResponse);

    }

    /**
     * Programa la baja de una indicación de prestación, cerrando su vigencia. Admite una
     * fecha futura para dejar el retiro agendado.
     *
     * @param id {@code UUID} identificador de la indicación
     * @param scheduleBajaIndicacionPrestacionRequest {@code ScheduleBajaIndicacionPrestacionRequest} fecha de fin de vigencia opcional
     * @return {@code ResponseEntity<ScheduleBajaIndicacionPrestacionResponse>} la confirmación de la baja programada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('PREST_BAJA')")
    @PatchMapping("/IndicacionPrestacion/{id}/Baja")
    public ResponseEntity<ScheduleBajaIndicacionPrestacionResponse> scheduleBajaIndicacionPrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleBajaIndicacionPrestacionRequest scheduleBajaIndicacionPrestacionRequest) {

        log.info("Solicitud recibida: dar de baja indicación de prestación id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(scheduleBajaIndicacionPrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, scheduleBajaIndicacionPrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        ScheduleBajaIndicacionPrestacionResponse scheduleBajaIndicacionPrestacionResponse = indicacionPrestacionApp
                .scheduleBajaIndicacionPrestacion(scheduleBajaIndicacionPrestacionRequest);

        return ResponseEntity.ok(scheduleBajaIndicacionPrestacionResponse);

    }

    /**
     * Busca la indicación de prestación vigente que cumple el criteria de filtrado
     * dinámico proporcionado. A diferencia de {@link #findIndicacionesPrestacion}, devuelve
     * una única indicación (no paginada) — pensado para criterios que identifican una
     * indicación puntual (ej. {@code id.equals}).
     *
     * @param indicacionPrestacionCriteria {@code IndicacionPrestacionCriteria} filtros a aplicar
     *        (ver {@code Docs/ARQUITECTURA.md §7})
     * @return {@code ResponseEntity<GetIndicacionPrestacionResponse>} la indicación encontrada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_CONSULTAR')")
    @GetMapping("/IndicacionPrestacion/Buscar")
    public ResponseEntity<GetIndicacionPrestacionResponse> findIndicacionPrestacionByCriteria(
            @ParameterObject IndicacionPrestacionCriteria indicacionPrestacionCriteria) {

        log.info("Solicitud recibida: buscar indicación de prestación criteria={}", indicacionPrestacionCriteria);

        GetIndicacionPrestacionResponse getIndicacionPrestacionResponse = indicacionPrestacionQueryService
                .findIndicacionPrestacionByCriteria(indicacionPrestacionCriteria);

        return ResponseEntity.ok(getIndicacionPrestacionResponse);

    }

    /**
     * Lista indicaciones de prestación vigentes según el criteria de filtrado dinámico
     * proporcionado.
     *
     * @param indicacionPrestacionCriteria {@code IndicacionPrestacionCriteria} filtros a aplicar
     *        (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListIndicacionPrestacionResponse>>} página de indicaciones (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_CONSULTAR')")
    @GetMapping("/IndicacionPrestacion")
    public ResponseEntity<PageResponse<ListIndicacionPrestacionResponse>> findIndicacionesPrestacion(
            @ParameterObject IndicacionPrestacionCriteria indicacionPrestacionCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {

        log.info("Solicitud recibida: listar indicaciones de prestación criteria={} page={}", indicacionPrestacionCriteria, pageable);

        PageResponse<ListIndicacionPrestacionResponse> pageResponse = indicacionPrestacionQueryService
                .findIndicacionesPrestacion(indicacionPrestacionCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
