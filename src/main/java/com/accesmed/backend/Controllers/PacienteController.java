package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.PacienteApp;
import com.accesmed.backend.Records.Paciente.Criteria.PacienteCriteria;
import com.accesmed.backend.Records.Paciente.Request.CreatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Request.UpdatePacienteRequest;
import com.accesmed.backend.Records.Paciente.Response.CreatePacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.GetPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.ListPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.SoftDeletePacienteResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.PacienteQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Controlador REST para los endpoints de Paciente.
 * Recibe requests, valida que el id de la ruta coincida con el del body cuando
 * corresponde, delega en el caso de uso {@code PacienteApp} (escritura) o
 * {@code PacienteQueryService} (lectura), y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Paciente")
public class PacienteController {

    //region ========== Dependencias o inyecciones ==========

    private final PacienteApp pacienteApp;
    private final PacienteQueryService pacienteQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un paciente nuevo junto con las coberturas de obra social existentes que
     * declara (alta atómica).
     *
     * @param createPacienteRequest {@code CreatePacienteRequest} datos del paciente y sus coberturas
     * @return {@code ResponseEntity<CreatePacienteResponse>} el paciente creado (HTTP 201)
     */
    @PostMapping("/Paciente")
    public ResponseEntity<CreatePacienteResponse> createPaciente(
            @Valid @RequestBody CreatePacienteRequest createPacienteRequest) {

        log.info("Solicitud recibida: crear paciente dni={}", createPacienteRequest.dni());

        CreatePacienteResponse createPacienteResponse = pacienteApp.createPaciente(createPacienteRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createPacienteResponse);

    }

    /**
     * Actualiza un paciente existente.
     *
     * @param id {@code UUID} identificador del paciente
     * @param updatePacienteRequest {@code UpdatePacienteRequest} datos a actualizar
     * @return {@code ResponseEntity<GetPacienteResponse>} el paciente actualizado (HTTP 200)
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     */
    @PatchMapping("/Paciente/{id}")
    public ResponseEntity<GetPacienteResponse> updatePaciente(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePacienteRequest updatePacienteRequest) {

        log.info("Solicitud recibida: actualizar paciente id={}", id);

        //Verificar que el id de la ruta coincida con el del body
        if (!id.equals(updatePacienteRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePacienteRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        //Invocar caso de uso
        GetPacienteResponse getPacienteResponse = pacienteApp.updatePaciente(updatePacienteRequest);

        //Devolver Respuesta
        return ResponseEntity.ok(getPacienteResponse);

    }

    /**
     * Busca el paciente activo que cumple el criteria de filtrado dinámico proporcionado,
     * con sus coberturas de obra social. A diferencia de {@link #findPacientes}, devuelve
     * un único paciente (no paginado) — pensado para criterios que identifican un paciente
     * puntual (ej. {@code id.equals}).
     *
     * @param pacienteCriteria {@code PacienteCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @return {@code ResponseEntity<GetPacienteResponse>} el paciente encontrado (HTTP 200)
     */
    @GetMapping("/Paciente/Buscar")
    public ResponseEntity<GetPacienteResponse> findPacienteByCriteria(@ParameterObject PacienteCriteria pacienteCriteria) {

        log.info("Solicitud recibida: buscar paciente criteria={}", pacienteCriteria);

        GetPacienteResponse getPacienteResponse = pacienteQueryService.findPacienteByCriteria(pacienteCriteria);

        return ResponseEntity.ok(getPacienteResponse);

    }

    /**
     * Lista pacientes activos según el criteria de filtrado dinámico proporcionado.
     *
     * @param pacienteCriteria {@code PacienteCriteria} filtros a aplicar (ver {@code Docs/ARQUITECTURA.md §7})
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListPacienteResponse>>} página de pacientes (HTTP 200)
     */
    @GetMapping("/Paciente")
    public ResponseEntity<PageResponse<ListPacienteResponse>> findPacientes(
            @ParameterObject PacienteCriteria pacienteCriteria,
            @ParameterObject @PageableDefault(size = 20, sort = "apellido") Pageable pageable) {

        log.info("Solicitud recibida: listar pacientes criteria={} page={}", pacienteCriteria, pageable);

        PageResponse<ListPacienteResponse> pageResponse = pacienteQueryService.findPacientes(pacienteCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Da de baja un paciente (baja lógica restrictiva contra turnos vivos).
     *
     * @param id {@code UUID} identificador del paciente
     * @return {@code ResponseEntity<SoftDeletePacienteResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/Paciente/{id}")
    public ResponseEntity<SoftDeletePacienteResponse> softDeletePaciente(@PathVariable UUID id) {

        log.info("Solicitud recibida: dar de baja paciente id={}", id);

        SoftDeletePacienteResponse softDeletePacienteResponse = pacienteApp.softDeletePaciente(id);

        return ResponseEntity.ok(softDeletePacienteResponse);

    }

    //endregion

}
