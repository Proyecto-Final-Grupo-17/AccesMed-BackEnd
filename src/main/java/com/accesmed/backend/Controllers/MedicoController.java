package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.MedicoApp;
import com.accesmed.backend.Records.Medico.Criteria.MedicoCriteria;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.QueryServices.MedicoQueryService;
import com.accesmed.backend.Records.Medico.Request.CreateMedicoRequest;
import com.accesmed.backend.Records.Medico.Request.UpdateMedicoRequest;
import com.accesmed.backend.Records.Medico.Response.CreateMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.GetMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.ListMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.SoftDeleteMedicoResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Controlador REST para los endpoints de Médico.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code MedicoApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Medico")
public class MedicoController {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoApp medicoApp;
    private final MedicoQueryService medicoQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un médico nuevo junto con las prestaciones existentes que atiende (alta atómica).
     *
     * @param createMedicoRequest {@code CreateMedicoRequest} datos del médico y sus prestaciones
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada del usuario que realiza la operación
     * @return {@code ResponseEntity<CreateMedicoResponse>} el médico creado (HTTP 201)
     */
    @PreAuthorize("hasAuthority('MED_ALTA')")
    @PostMapping("/Medico")
    public ResponseEntity<CreateMedicoResponse> createMedico(
            @Valid @RequestBody CreateMedicoRequest createMedicoRequest,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: crear médico matrícula={}", createMedicoRequest.matricula());

        CreateMedicoResponse createMedicoResponse = medicoApp.createMedico(createMedicoRequest, usuarioDetails);

        return ResponseEntity.status(HttpStatus.CREATED).body(createMedicoResponse);

    }

    /**
     * Actualiza un médico existente.
     *
     * @param id {@code UUID} identificador del médico
     * @param updateMedicoRequest {@code UpdateMedicoRequest} datos a actualizar
     * @return {@code ResponseEntity<GetMedicoResponse>} el médico actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PreAuthorize("hasAuthority('MED_MODIFICAR')")
    @PatchMapping("/Medico/{id}")
    public ResponseEntity<GetMedicoResponse> updateMedico(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMedicoRequest updateMedicoRequest) {

        log.info("Solicitud recibida: actualizar médico id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(updateMedicoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateMedicoRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        //Invocar caso de uso
        GetMedicoResponse getMedicoResponse = medicoApp.updateMedico(updateMedicoRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(getMedicoResponse);

    }

    /**
     * Busca el médico activo que cumple el criteria de filtrado dinámico proporcionado,
     * con sus prestaciones. A diferencia de {@link #findMedicos}, devuelve un único médico
     * (no paginado) — pensado para criterios que identifican un médico puntual (ej.
     * {@code id.equals}).
     *
     * @param medicoCriteria {@code MedicoCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<GetMedicoResponse>} el médico encontrado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('MED_CONSULTAR')")
    @GetMapping("/Medico/Buscar")
    public ResponseEntity<GetMedicoResponse> findMedicoByCriteria(@ParameterObject MedicoCriteria medicoCriteria,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: buscar médico criteria={}", medicoCriteria);

        GetMedicoResponse getMedicoResponse = medicoQueryService.findMedicoByCriteria(medicoCriteria, usuarioDetails);

        return ResponseEntity.ok(getMedicoResponse);

    }

    /**
     * Lista médicos activos según el criteria de filtrado dinámico proporcionado.
     *
     * @param medicoCriteria {@code MedicoCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListMedicoResponse>>} página de médicos (HTTP 200)
     */
    @PreAuthorize("hasAuthority('MED_CONSULTAR')")
    @GetMapping("/Medico")
    public ResponseEntity<PageResponse<ListMedicoResponse>> findMedicos(
            @ParameterObject MedicoCriteria medicoCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "apellido") Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar médicos criteria={} page={}", medicoCriteria, pageable);

        PageResponse<ListMedicoResponse> pageResponse = medicoQueryService.findMedicos(medicoCriteria, pageable, usuarioDetails);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Da de baja un médico (baja lógica restrictiva contra turnos vivos).
     *
     * @param id {@code UUID} identificador del médico
     * @return {@code ResponseEntity<SoftDeleteMedicoResponse>} la confirmación de la baja (HTTP 200)
     */
    @PreAuthorize("hasAuthority('MED_BAJA')")
    @DeleteMapping("/Medico/{id}")
    public ResponseEntity<SoftDeleteMedicoResponse> softDeleteMedico(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja médico id={}", id);

        SoftDeleteMedicoResponse softDeleteMedicoResponse = medicoApp.softDeleteMedico(id);

        return ResponseEntity.ok(softDeleteMedicoResponse);

    }

    //endregion

}
