package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.ObraSocialPrestacionApp;
import com.accesmed.backend.Records.ObraSocialPrestacion.Criteria.ObraSocialPrestacionCriteria;
import com.accesmed.backend.Records.ObraSocialPrestacion.Request.AssignObraSocialPrestacionRequest;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.GetObraSocialPrestacionResponse;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.ListObraSocialPrestacionResponse;
import com.accesmed.backend.Records.ObraSocialPrestacion.Response.UnassignObraSocialPrestacionResponse;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.ObraSocialPlanPrestacionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Obra Social-Prestación. No es CRUD completo:
 * asigna, desasigna y lista las coberturas de prestaciones de los planes. Recibe
 * requests, delega en el caso de uso {@code ObraSocialPrestacionApp} (escritura) o en
 * {@code ObraSocialPlanPrestacionQueryService} (lectura) y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/ObraSocialPrestacion")
public class ObraSocialPrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPrestacionApp obraSocialPrestacionApp;
    private final ObraSocialPlanPrestacionQueryService obraSocialPlanPrestacionQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna una prestación existente a un plan existente, con sus condiciones de
     * cobertura.
     *
     * @param assignObraSocialPrestacionRequest {@code AssignObraSocialPrestacionRequest} datos de la cobertura
     * @return {@code ResponseEntity<GetObraSocialPrestacionResponse>} la cobertura creada (HTTP 201)
     */
    @PostMapping("/Asignar")
    public ResponseEntity<GetObraSocialPrestacionResponse> assignPrestacion(
            @Valid @RequestBody AssignObraSocialPrestacionRequest assignObraSocialPrestacionRequest) {

        log.info("Solicitud recibida: asignar prestación {} a plan {}",
                assignObraSocialPrestacionRequest.prestacionId(), assignObraSocialPrestacionRequest.planId());

        GetObraSocialPrestacionResponse getObraSocialPrestacionResponse = obraSocialPrestacionApp.assignPrestacion(assignObraSocialPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(getObraSocialPrestacionResponse);

    }

    /**
     * Desasigna una prestación de un plan (baja lógica de la cobertura). Restrictiva:
     * rechaza si hay turnos vivos cubiertos por el par plan-prestación.
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code ResponseEntity<UnassignObraSocialPrestacionResponse>} la confirmación de la baja (HTTP 200)
     */
    @PatchMapping("/Desasignar/{id}")
    public ResponseEntity<UnassignObraSocialPrestacionResponse> unassignPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: desasignar cobertura de plan, id={}", id);

        UnassignObraSocialPrestacionResponse unassignObraSocialPrestacionResponse = obraSocialPrestacionApp.unassignPrestacion(id);

        return ResponseEntity.ok(unassignObraSocialPrestacionResponse);

    }

    /**
     * Lista coberturas plan-prestación según el criteria de filtrado dinámico
     * proporcionado (ej. {@code planId.equals=} para las prestaciones de un plan puntual).
     *
     * @param obraSocialPrestacionCriteria {@code ObraSocialPrestacionCriteria} filtros a aplicar
     *        (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListObraSocialPrestacionResponse>>} página de coberturas (HTTP 200)
     */
    @GetMapping("/ObraSocialPrestacion")
    public ResponseEntity<PageResponse<ListObraSocialPrestacionResponse>> findObraSocialPrestaciones(
            @ParameterObject ObraSocialPrestacionCriteria obraSocialPrestacionCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "prestacion.nombre") Pageable pageable) {

        log.info("Solicitud recibida: listar coberturas plan-prestación criteria={} page={}", obraSocialPrestacionCriteria, pageable);

        PageResponse<ListObraSocialPrestacionResponse> pageResponse =
                obraSocialPlanPrestacionQueryService.findObraSocialPrestaciones(obraSocialPrestacionCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
