package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.TipoIndicacionPrestacionApp;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Criteria.TipoIndicacionPrestacionCriteria;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.UpdateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Request.CreateTipoIndicacionPrestacionRequest;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.UpdateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.CreateTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.ListTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.GetTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Records.TipoIndicacionPrestacion.Response.SoftDeleteTipoIndicacionPrestacionResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.TipoIndicacionPrestacionQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Recibe requests, delega en el caso de uso {@code TipoIndicacionPrestacionApp} (escritura) o
 * {@code TipoIndicacionPrestacionQueryService} (lectura), y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/TipoIndicacionPrestacion")
public class TipoIndicacionPrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final TipoIndicacionPrestacionApp tipoIndicacionPrestacionApp;
    private final TipoIndicacionPrestacionQueryService tipoIndicacionPrestacionQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un tipo de indicación de prestación nuevo.
     *
     * @param createTipoIndicacionPrestacionRequest {@code CreateTipoIndicacionPrestacionRequest} datos del tipo
     * @return {@code ResponseEntity<CreateTipoIndicacionPrestacionResponse>} el tipo creado (HTTP 201)
     */
    @PreAuthorize("hasAuthority('PREST_ALTA')")
    @PostMapping("/TipoIndicacionPrestacion")
    public ResponseEntity<CreateTipoIndicacionPrestacionResponse> createTipoIndicacionPrestacion(
            @Valid @RequestBody CreateTipoIndicacionPrestacionRequest createTipoIndicacionPrestacionRequest) {

        log.info("Solicitud recibida: crear tipo de indicación código={}", createTipoIndicacionPrestacionRequest.codigo());

        CreateTipoIndicacionPrestacionResponse createTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionApp
                .createTipoIndicacionPrestacion(createTipoIndicacionPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createTipoIndicacionPrestacionResponse);

    }

    /**
     * Actualiza un tipo de indicación de prestación existente.
     *
     * @param id {@code UUID} identificador del tipo
     * @param updateTipoIndicacionPrestacionRequest {@code UpdateTipoIndicacionPrestacionRequest} datos a actualizar
     * @return {@code ResponseEntity<UpdateTipoIndicacionPrestacionResponse>} el tipo actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('PREST_MODIFICAR')")
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

        UpdateTipoIndicacionPrestacionResponse updateTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionApp
                .updateTipoIndicacionPrestacion(updateTipoIndicacionPrestacionRequest);

        return ResponseEntity.ok(updateTipoIndicacionPrestacionResponse);

    }

    /**
     * Da de baja un tipo de indicación de prestación (baja lógica y restrictiva).
     *
     * @param id {@code UUID} identificador del tipo
     * @return {@code ResponseEntity<SoftDeleteTipoIndicacionPrestacionResponse>} la confirmación de la baja (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_BAJA')")
    @DeleteMapping("/TipoIndicacionPrestacion/{id}")
    public ResponseEntity<SoftDeleteTipoIndicacionPrestacionResponse> softDeleteTipoIndicacionPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja tipo de indicación id={}", id);

        SoftDeleteTipoIndicacionPrestacionResponse softDeleteTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionApp
                .softDeleteTipoIndicacionPrestacion(id);

        return ResponseEntity.ok(softDeleteTipoIndicacionPrestacionResponse);

    }

    /**
     * Busca el tipo de indicación de prestación activo que cumple el criteria de filtrado
     * dinámico proporcionado. A diferencia de {@link #findTiposIndicacionPrestacion},
     * devuelve un único tipo (no paginado) — pensado para criterios que identifican un tipo
     * puntual (ej. {@code id.equals}).
     *
     * @param tipoIndicacionPrestacionCriteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar
     *        (ver {@code Docs/ARQUITECTURA.md §7})
     * @return {@code ResponseEntity<GetTipoIndicacionPrestacionResponse>} el tipo encontrado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_CONSULTAR')")
    @GetMapping("/TipoIndicacionPrestacion/Buscar")
    public ResponseEntity<GetTipoIndicacionPrestacionResponse> findTipoIndicacionPrestacionByCriteria(
            @ParameterObject TipoIndicacionPrestacionCriteria tipoIndicacionPrestacionCriteria) {

        log.info("Solicitud recibida: buscar tipo de indicación criteria={}", tipoIndicacionPrestacionCriteria);

        GetTipoIndicacionPrestacionResponse getTipoIndicacionPrestacionResponse = tipoIndicacionPrestacionQueryService
                .findTipoIndicacionPrestacionByCriteria(tipoIndicacionPrestacionCriteria);

        return ResponseEntity.ok(getTipoIndicacionPrestacionResponse);

    }

    /**
     * Lista tipos de indicación de prestación activos según el criteria de filtrado
     * dinámico proporcionado.
     *
     * @param tipoIndicacionPrestacionCriteria {@code TipoIndicacionPrestacionCriteria} filtros a aplicar
     *        (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListTipoIndicacionPrestacionResponse>>} página de tipos (HTTP 200)
     */
    @PreAuthorize("hasAuthority('PREST_CONSULTAR')")
    @GetMapping("/TipoIndicacionPrestacion")
    public ResponseEntity<PageResponse<ListTipoIndicacionPrestacionResponse>> findTiposIndicacionPrestacion(
            @ParameterObject TipoIndicacionPrestacionCriteria tipoIndicacionPrestacionCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {

        log.info("Solicitud recibida: listar tipos de indicación criteria={} page={}", tipoIndicacionPrestacionCriteria, pageable);

        PageResponse<ListTipoIndicacionPrestacionResponse> pageResponse = tipoIndicacionPrestacionQueryService
                .findTiposIndicacionPrestacion(tipoIndicacionPrestacionCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
