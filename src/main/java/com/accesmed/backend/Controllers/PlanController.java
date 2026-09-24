package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.PlanApp;
import com.accesmed.backend.Records.Plan.Criteria.PlanCriteria;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.DeshabilitarPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.PlanQueryService;
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
 * Controlador REST para los endpoints de Plan. Propio, no anidado bajo Obra Social.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code PlanApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Plan")
public class PlanController {

    //region ========== Dependencias o inyecciones ==========

    private final PlanApp planApp;
    private final PlanQueryService planQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Agrega un plan nuevo a una obra social activa existente.
     *
     * @param addPlanRequest {@code AddPlanRequest} datos del plan a agregar
     * @return {@code ResponseEntity<GetPlanResponse>} el plan agregado (HTTP 201)
     */
    @PreAuthorize("hasAuthority('OS_ALTA')")
    @PostMapping("/Plan")
    public ResponseEntity<GetPlanResponse> createPlan(@Valid @RequestBody AddPlanRequest addPlanRequest) {

        log.info("Solicitud recibida: agregar plan obraSocialId={} código={}", addPlanRequest.obraSocialId(), addPlanRequest.codigo());

        GetPlanResponse getPlanResponse = planApp.createPlan(addPlanRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(getPlanResponse);

    }

    /**
     * Actualiza código y nombre de un plan existente.
     *
     * @param id {@code UUID} identificador del plan
     * @param updatePlanRequest {@code UpdatePlanRequest} datos a actualizar
     * @return {@code ResponseEntity<GetPlanResponse>} el plan actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('OS_MODIFICAR')")
    @PatchMapping("/Plan/{id}")
    public ResponseEntity<GetPlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest updatePlanRequest) {

        log.info("Solicitud recibida: actualizar plan id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(updatePlanRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePlanRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        //Invocar caso de uso
        GetPlanResponse getPlanResponse = planApp.updatePlan(updatePlanRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(getPlanResponse);

    }

    /**
     * Publica un plan (transición reversible).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code ResponseEntity<CambioEstadoPlanResponse>} el plan publicado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_MODIFICAR')")
    @PatchMapping("/Plan/{id}/Publicar")
    public ResponseEntity<CambioEstadoPlanResponse> publishPlan(@PathVariable UUID id) {

        log.info("Solicitud recibida: publicar plan id={}", id);

        CambioEstadoPlanResponse cambioEstadoPlanResponse = planApp.publishPlan(id);

        return ResponseEntity.ok(cambioEstadoPlanResponse);

    }

    /**
     * Despublica un plan (transición reversible).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code ResponseEntity<CambioEstadoPlanResponse>} el plan despublicado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_MODIFICAR')")
    @PatchMapping("/Plan/{id}/Despublicar")
    public ResponseEntity<CambioEstadoPlanResponse> unpublishPlan(@PathVariable UUID id) {

        log.info("Solicitud recibida: despublicar plan id={}", id);

        CambioEstadoPlanResponse cambioEstadoPlanResponse = planApp.unpublishPlan(id);

        return ResponseEntity.ok(cambioEstadoPlanResponse);

    }

    /**
     * Deshabilita un plan (transición terminal e irreversible, restrictiva).
     *
     * @param id {@code UUID} identificador del plan
     * @param deshabilitarPlanRequest {@code DeshabilitarPlanRequest} motivo opcional
     * @return {@code ResponseEntity<CambioEstadoPlanResponse>} el plan deshabilitado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('OS_BAJA')")
    @PatchMapping("/Plan/{id}/Deshabilitar")
    public ResponseEntity<CambioEstadoPlanResponse> disablePlan(
            @PathVariable UUID id,
            @Valid @RequestBody DeshabilitarPlanRequest deshabilitarPlanRequest) {

        log.info("Solicitud recibida: deshabilitar plan id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(deshabilitarPlanRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, deshabilitarPlanRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        //Invocar caso de uso
        CambioEstadoPlanResponse cambioEstadoPlanResponse = planApp.disablePlan(deshabilitarPlanRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(cambioEstadoPlanResponse);

    }

    /**
     * Busca el plan que cumple el criteria de filtrado dinámico proporcionado. A
     * diferencia de {@link #findPlanes}, devuelve un único plan (no paginado) — pensado
     * para criterios que identifican un plan puntual (ej. {@code id.equals}).
     *
     * @param planCriteria {@code PlanCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<GetPlanResponse>} el plan encontrado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_CONSULTAR')")
    @GetMapping("/Plan/Buscar")
    public ResponseEntity<GetPlanResponse> findPlanByCriteria(
            @ParameterObject PlanCriteria planCriteria,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: buscar plan criteria={}", planCriteria);

        GetPlanResponse getPlanResponse = planQueryService.findPlanByCriteria(planCriteria, usuarioDetails);

        return ResponseEntity.ok(getPlanResponse);

    }

    /**
     * Lista planes según el criteria de filtrado dinámico proporcionado.
     *
     * @param planCriteria {@code PlanCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListPlanResponse>>} página de planes (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_CONSULTAR')")
    @GetMapping("/Plan")
    public ResponseEntity<PageResponse<ListPlanResponse>> findPlanes(
            @ParameterObject PlanCriteria planCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar planes criteria={} page={}", planCriteria, pageable);

        PageResponse<ListPlanResponse> pageResponse = planQueryService.findPlanes(planCriteria, pageable, usuarioDetails);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
