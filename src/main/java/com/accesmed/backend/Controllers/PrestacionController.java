package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.PrestacionApp;
import com.accesmed.backend.Records.Prestacion.Criteria.PrestacionCriteria;
import com.accesmed.backend.Records.Prestacion.Request.CreatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.DeshabilitarPrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Request.UpdatePrestacionRequest;
import com.accesmed.backend.Records.Prestacion.Response.CambioEstadoPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.CreatePrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.ListPrestacionResponse;
import com.accesmed.backend.Records.Prestacion.Response.UpdatePrestacionResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.PrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PrestacionQueryService prestacionQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una prestación nueva.
     *
     * @param createPrestacionRequest {@code CreatePrestacionRequest} datos de la prestación
     * @return {@code ResponseEntity<CreatePrestacionResponse>} la prestación creada (HTTP 201)
     */
    @PreAuthorize("hasAuthority('PREST_ALTA')")
    @PostMapping("/Prestacion")
    public ResponseEntity<CreatePrestacionResponse> createPrestacion(
            @Valid @RequestBody CreatePrestacionRequest createPrestacionRequest) {

        log.info("Solicitud recibida: crear prestación código={}", createPrestacionRequest.codigo());

        CreatePrestacionResponse createPrestacionResponse = prestacionApp.createPrestacion(createPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createPrestacionResponse);

    }

    /**
     * Actualiza nombre y tolerancias/duraciones de una prestación.
     *
     * @param id {@code UUID} identificador de la prestación
     * @param updatePrestacionRequest {@code UpdatePrestacionRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdatePrestacionResponse>} la prestación actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('PREST_MODIFICAR')")
    @PatchMapping("/Prestacion/{id}")
    public ResponseEntity<UpdatePrestacionResponse> updatePrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePrestacionRequest updatePrestacionRequest) {

        log.info("Solicitud recibida: actualizar prestación id={}", id);

        //Verificar que el id de la ruta coincida con el del body.
        if (!id.equals(updatePrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso.
        UpdatePrestacionResponse updatePrestacionResponse = prestacionApp.updatePrestacion(updatePrestacionRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(updatePrestacionResponse);

    }

    /**
     * Publica una prestación (transición reversible).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code ResponseEntity<CambioEstadoPrestacionResponse>} la prestación publicada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_MODIFICAR')")
    @PatchMapping("/Prestacion/{id}/Publicar")
    public ResponseEntity<CambioEstadoPrestacionResponse> publishPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: publicar prestación id={}", id);

        //Invocar caso de uso.
        CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionApp.publishPrestacion(id);

        //Devolver Respuesta
        return ResponseEntity.ok(cambioEstadoPrestacionResponse);

    }

    /**
     * Despublica una prestación (transición reversible).
     *
     * @param id {@code UUID} identificador de la prestación
     * @return {@code ResponseEntity<CambioEstadoPrestacionResponse>} la prestación despublicada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_MODIFICAR')")
    @PatchMapping("/Prestacion/{id}/Despublicar")
    public ResponseEntity<CambioEstadoPrestacionResponse> unpublishPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: despublicar prestación id={}", id);

        //Invocar caso de uso.
        CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionApp.unpublishPrestacion(id);

        //Devolver Respuesta
        return ResponseEntity.ok(cambioEstadoPrestacionResponse);

    }

    /**
     * Deshabilita una prestación (transición terminal e irreversible, restrictiva).
     *
     * @param id {@code UUID} identificador de la prestación
     * @param deshabilitarPrestacionRequest {@code DeshabilitarPrestacionRequest} motivo opcional
     * @return {@code ResponseEntity<CambioEstadoPrestacionResponse>} la prestación deshabilitada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('PREST_BAJA')")
    @PatchMapping("/Prestacion/{id}/Deshabilitar")
    public ResponseEntity<CambioEstadoPrestacionResponse> disablePrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody DeshabilitarPrestacionRequest deshabilitarPrestacionRequest) {

        log.info("Solicitud recibida: deshabilitar prestación id={}", id);

        //Verificar que el id de la ruta coincida con el del body.
        if (!id.equals(deshabilitarPrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, deshabilitarPrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso.
        CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionApp.disablePrestacion(deshabilitarPrestacionRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(cambioEstadoPrestacionResponse);

    }

    /**
     * Lista prestaciones según el criteria de filtrado dinámico proporcionado.
     *
     * @param prestacionCriteria {@code PrestacionCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListPrestacionResponse>>} página de prestaciones (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_CONSULTAR')")
    @GetMapping("/Prestacion")
    public ResponseEntity<PageResponse<ListPrestacionResponse>> findPrestaciones(
            @ParameterObject PrestacionCriteria prestacionCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar prestaciones criteria={} page={}", prestacionCriteria, pageable);

        PageResponse<ListPrestacionResponse> pageResponse =
                prestacionQueryService.findPrestaciones(prestacionCriteria, pageable, usuarioDetails);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
