package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.ObraSocialApp;
import com.accesmed.backend.Records.ObraSocial.Criteria.ObraSocialCriteria;
import com.accesmed.backend.Records.ObraSocial.Request.CreateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Request.UpdateObraSocialRequest;
import com.accesmed.backend.Records.ObraSocial.Response.CreateObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.ListObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.SoftDeleteObraSocialResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.ObraSocialQueryService;
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
 * Controlador REST para los endpoints de Obra Social.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code ObraSocialApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/ObraSocial")
public class ObraSocialController {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialApp obraSocialApp;
    private final ObraSocialQueryService obraSocialQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una obra social nueva junto con sus planes iniciales (alta atómica).
     *
     * @param createObraSocialRequest {@code CreateObraSocialRequest} datos de la obra social y sus planes
     * @return {@code ResponseEntity<CreateObraSocialResponse>} la obra social creada (HTTP 201)
     */
    @PreAuthorize("hasAuthority('OS_ALTA')")
    @PostMapping("/ObraSocial")
    public ResponseEntity<CreateObraSocialResponse> createObraSocial(
            @Valid @RequestBody CreateObraSocialRequest createObraSocialRequest) {

        log.info("Solicitud recibida: crear obra social código={}", createObraSocialRequest.codigo());

        CreateObraSocialResponse createObraSocialResponse = obraSocialApp.createObraSocial(createObraSocialRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createObraSocialResponse);

    }

    /**
     * Actualiza una obra social existente.
     *
     * @param id {@code UUID} identificador de la obra social
     * @param updateObraSocialRequest {@code UpdateObraSocialRequest} datos a actualizar
     * @return {@code ResponseEntity<GetObraSocialResponse>} la obra social actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('OS_MODIFICAR')")
    @PatchMapping("/ObraSocial/{id}")
    public ResponseEntity<GetObraSocialResponse> updateObraSocial(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateObraSocialRequest updateObraSocialRequest) {

        log.info("Solicitud recibida: actualizar obra social id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(updateObraSocialRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateObraSocialRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso
        GetObraSocialResponse getObraSocialResponse = obraSocialApp.updateObraSocial(updateObraSocialRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(getObraSocialResponse);

    }

    /**
     * Busca la obra social activa que cumple el criteria de filtrado dinámico
     * proporcionado, con sus planes. A diferencia de {@link #findObrasSociales}, devuelve
     * una única obra social (no paginada) — pensado para criterios que identifican una obra
     * social puntual (ej. {@code id.equals}).
     *
     * @param obraSocialCriteria {@code ObraSocialCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @return {@code ResponseEntity<GetObraSocialResponse>} la obra social encontrada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_CONSULTAR')")
    @GetMapping("/ObraSocial/Buscar")
    public ResponseEntity<GetObraSocialResponse> findObraSocialByCriteria(@ParameterObject ObraSocialCriteria obraSocialCriteria) {

        log.info("Solicitud recibida: buscar obra social criteria={}", obraSocialCriteria);

        GetObraSocialResponse getObraSocialResponse = obraSocialQueryService.findObraSocialByCriteria(obraSocialCriteria);

        return ResponseEntity.ok(getObraSocialResponse);

    }

    /**
     * Lista obras sociales activas según el criteria de filtrado dinámico proporcionado.
     *
     * @param obraSocialCriteria {@code ObraSocialCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListObraSocialResponse>>} página de obras sociales (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_CONSULTAR')")
    @GetMapping("/ObraSocial")
    public ResponseEntity<PageResponse<ListObraSocialResponse>> findObrasSociales(
            @ParameterObject ObraSocialCriteria obraSocialCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {

        log.info("Solicitud recibida: listar obras sociales criteria={} page={}", obraSocialCriteria, pageable);

        PageResponse<ListObraSocialResponse> pageResponse = obraSocialQueryService.findObrasSociales(obraSocialCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Da de baja una obra social (baja lógica restrictiva por transitividad, con cascada
     * sobre sus planes).
     *
     * @param id {@code UUID} identificador de la obra social
     * @return {@code ResponseEntity<SoftDeleteObraSocialResponse>} la confirmación de la baja (HTTP 200)
     */
    @PreAuthorize("hasAuthority('OS_BAJA')")
    @DeleteMapping("/ObraSocial/{id}")
    public ResponseEntity<SoftDeleteObraSocialResponse> softDeleteObraSocial(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja obra social id={}", id);

        SoftDeleteObraSocialResponse softDeleteObraSocialResponse = obraSocialApp.softDeleteObraSocial(id);

        return ResponseEntity.ok(softDeleteObraSocialResponse);

    }

    //endregion

}
