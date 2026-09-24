package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.EspecialidadApp;
import com.accesmed.backend.Records.Especialidad.Criteria.EspecialidadCriteria;
import com.accesmed.backend.Records.Especialidad.Request.CreateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Request.UpdateEspecialidadRequest;
import com.accesmed.backend.Records.Especialidad.Response.CreateEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.GetEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.ListEspecialidadResponse;
import com.accesmed.backend.Records.Especialidad.Response.SoftDeleteEspecialidadResponse;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.EspecialidadQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Controlador REST para los endpoints de Especialidad.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code EspecialidadApp} (escritura) o
 * {@code EspecialidadQueryService} (lectura), y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Especialidad")
public class EspecialidadController {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadApp especialidadApp;
    private final EspecialidadQueryService especialidadQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una especialidad nueva.
     *
     * @param createEspecialidadRequest {@code CreateEspecialidadRequest} datos de la especialidad
     * @return {@code ResponseEntity<CreateEspecialidadResponse>} la especialidad creada (HTTP 201)
     */
    @PreAuthorize("hasAuthority('ESP_ALTA')")
    @PostMapping("/Especialidad")
    public ResponseEntity<CreateEspecialidadResponse> createEspecialidad(
            @Valid @RequestBody CreateEspecialidadRequest createEspecialidadRequest) {

        log.info("Solicitud recibida: crear especialidad código={}", createEspecialidadRequest.codigo());

        CreateEspecialidadResponse createEspecialidadResponse = especialidadApp.createEspecialidad(createEspecialidadRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createEspecialidadResponse);

    }

    /**
     * Actualiza una especialidad existente.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @param updateEspecialidadRequest {@code UpdateEspecialidadRequest} datos a actualizar
     * @return {@code ResponseEntity<GetEspecialidadResponse>} la especialidad actualizada (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('ESP_MODIFICAR')")
    @PatchMapping("/Especialidad/{id}")
    public ResponseEntity<GetEspecialidadResponse> updateEspecialidad(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEspecialidadRequest updateEspecialidadRequest) {

        log.info("Solicitud recibida: actualizar especialidad id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(updateEspecialidadRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateEspecialidadRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        //Invocar caso de uso
        GetEspecialidadResponse getEspecialidadResponse = especialidadApp.updateEspecialidad(updateEspecialidadRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(getEspecialidadResponse);

    }

    /**
     * Busca la especialidad activa que cumple el criteria de filtrado dinámico
     * proporcionado. A diferencia de {@link #findEspecialidades}, devuelve una única
     * especialidad (no paginada) — pensado para criterios que identifican una especialidad
     * puntual (ej. {@code id.equals}).
     *
     * @param especialidadCriteria {@code EspecialidadCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<GetEspecialidadResponse>} la especialidad encontrada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('ESP_CONSULTAR')")
    @GetMapping("/Especialidad/Buscar")
    public ResponseEntity<GetEspecialidadResponse> findEspecialidadByCriteria(
            @ParameterObject EspecialidadCriteria especialidadCriteria,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: buscar especialidad criteria={}", especialidadCriteria);

        GetEspecialidadResponse getEspecialidadResponse = especialidadQueryService.findEspecialidadByCriteria(especialidadCriteria, usuarioDetails);

        return ResponseEntity.ok(getEspecialidadResponse);

    }

    /**
     * Lista especialidades activas según el criteria de filtrado dinámico proporcionado.
     *
     * @param especialidadCriteria {@code EspecialidadCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListEspecialidadResponse>>} página de especialidades (HTTP 200)
     */
    @PreAuthorize("hasAuthority('ESP_CONSULTAR')")
    @GetMapping("/Especialidad")
    public ResponseEntity<PageResponse<ListEspecialidadResponse>> findEspecialidades(
            @ParameterObject EspecialidadCriteria especialidadCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar especialidades criteria={} page={}", especialidadCriteria, pageable);

        PageResponse<ListEspecialidadResponse> pageResponse = especialidadQueryService.findEspecialidades(especialidadCriteria, pageable, usuarioDetails);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Da de baja una especialidad (baja lógica restrictiva).
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code ResponseEntity<SoftDeleteEspecialidadResponse>} la confirmación de la baja (HTTP 200)
     */
    @PreAuthorize("hasAuthority('ESP_BAJA')")
    @DeleteMapping("/Especialidad/{id}")
    public ResponseEntity<SoftDeleteEspecialidadResponse> softDeleteEspecialidad(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja especialidad id={}", id);

        SoftDeleteEspecialidadResponse softDeleteEspecialidadResponse = especialidadApp.softDeleteEspecialidad(id);

        return ResponseEntity.ok(softDeleteEspecialidadResponse);

    }

    //endregion

}
