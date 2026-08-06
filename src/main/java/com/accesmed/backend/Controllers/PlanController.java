package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.PlanApp;
import com.accesmed.backend.Records.Plan.Request.AddPlanRequest;
import com.accesmed.backend.Records.Plan.Request.DeshabilitarPlanRequest;
import com.accesmed.backend.Records.Plan.Request.UpdatePlanRequest;
import com.accesmed.backend.Records.Plan.Response.CambioEstadoPlanResponse;
import com.accesmed.backend.Records.Plan.Response.GetPlanResponse;
import com.accesmed.backend.Records.Plan.Response.ListPlanResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    //endregion

    //region ========== Métodos ==========

    /**
     * Agrega un plan nuevo a una obra social activa existente.
     *
     * @param addPlanRequest {@code AddPlanRequest} datos del plan a agregar
     * @return {@code ResponseEntity<GetPlanResponse>} el plan agregado (HTTP 201)
     */
    @PostMapping("/Plan")
    public ResponseEntity<GetPlanResponse> addPlan(@Valid @RequestBody AddPlanRequest addPlanRequest) {

        log.info("Solicitud recibida: agregar plan obraSocialId={} código={}", addPlanRequest.obraSocialId(), addPlanRequest.codigo());

        GetPlanResponse response = planApp.addPlan(addPlanRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * Actualiza código y nombre de un plan existente.
     *
     * @param id {@code UUID} identificador del plan
     * @param updatePlanRequest {@code UpdatePlanRequest} datos a actualizar
     * @return {@code ResponseEntity<GetPlanResponse>} el plan actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/Plan/{id}")
    public ResponseEntity<GetPlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest updatePlanRequest) {

        log.info("Solicitud recibida: actualizar plan id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(updatePlanRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePlanRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso
        GetPlanResponse response = planApp.updatePlan(id, updatePlanRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(response);

    }

    /**
     * Publica un plan (transición reversible).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code ResponseEntity<CambioEstadoPlanResponse>} el plan publicado (HTTP 200)
     */
    @PatchMapping("/Plan/{id}/Publicar")
    public ResponseEntity<CambioEstadoPlanResponse> publicarPlan(@PathVariable UUID id) {

        log.info("Solicitud recibida: publicar plan id={}", id);

        CambioEstadoPlanResponse response = planApp.publicarPlan(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Despublica un plan (transición reversible).
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code ResponseEntity<CambioEstadoPlanResponse>} el plan despublicado (HTTP 200)
     */
    @PatchMapping("/Plan/{id}/Despublicar")
    public ResponseEntity<CambioEstadoPlanResponse> despublicarPlan(@PathVariable UUID id) {

        log.info("Solicitud recibida: despublicar plan id={}", id);

        CambioEstadoPlanResponse response = planApp.despublicarPlan(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Deshabilita un plan (transición terminal e irreversible, restrictiva).
     *
     * @param id {@code UUID} identificador del plan
     * @param deshabilitarPlanRequest {@code DeshabilitarPlanRequest} motivo opcional
     * @return {@code ResponseEntity<CambioEstadoPlanResponse>} el plan deshabilitado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/Plan/{id}/Deshabilitar")
    public ResponseEntity<CambioEstadoPlanResponse> deshabilitarPlan(
            @PathVariable UUID id,
            @Valid @RequestBody DeshabilitarPlanRequest deshabilitarPlanRequest) {

        log.info("Solicitud recibida: deshabilitar plan id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(deshabilitarPlanRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, deshabilitarPlanRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso
        CambioEstadoPlanResponse response = planApp.deshabilitarPlan(id, deshabilitarPlanRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(response);

    }

    /**
     * Obtiene un plan por su identificador.
     *
     * @param id {@code UUID} identificador del plan
     * @return {@code ResponseEntity<GetPlanResponse>} el plan encontrado (HTTP 200)
     */
    @GetMapping("/Plan/{id}")
    public ResponseEntity<GetPlanResponse> findPlanById(@PathVariable UUID id) {

        log.info("Solicitud recibida: obtener plan id={}", id);

        GetPlanResponse response = planApp.findPlanById(id);

        return ResponseEntity.ok(response);

    }

    /**
     * Lista los planes de una obra social determinada.
     *
     * @param obraSocialId {@code UUID} identificador de la obra social
     * @return {@code ResponseEntity<List<ListPlanResponse>>} lista de planes de esa obra social (HTTP 200)
     */
    @GetMapping("/Plan")
    public ResponseEntity<List<ListPlanResponse>> findPlanesByObraSocial(@RequestParam UUID obraSocialId) {

        log.info("Solicitud recibida: listar planes obraSocialId={}", obraSocialId);

        List<ListPlanResponse> response = planApp.findPlanesByObraSocial(obraSocialId);

        return ResponseEntity.ok(response);

    }

    //endregion

}
