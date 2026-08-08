package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.ObraSocialPacienteApp;
import com.accesmed.backend.Records.ObraSocialPaciente.Request.AssignObraSocialPacienteRequest;
import com.accesmed.backend.Records.ObraSocialPaciente.Response.GetObraSocialPacienteResponse;
import com.accesmed.backend.Records.ObraSocialPaciente.Response.SoftDeleteObraSocialPacienteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Obra Social-Paciente. No es CRUD completo: solo
 * asigna y desasigna la cobertura de un paciente. Recibe requests, delega en el caso de
 * uso {@code ObraSocialPacienteApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/ObraSocialPaciente")
public class ObraSocialPacienteController {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialPacienteApp obraSocialPacienteApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna a un paciente existente una cobertura sobre un plan existente de una obra
     * social existente.
     *
     * @param assignObraSocialPacienteRequest {@code AssignObraSocialPacienteRequest} datos de la cobertura
     * @return {@code ResponseEntity<GetObraSocialPacienteResponse>} la cobertura creada (HTTP 201)
     */
    @PostMapping("/Asignar")
    public ResponseEntity<GetObraSocialPacienteResponse> assignObraSocial(
            @Valid @RequestBody AssignObraSocialPacienteRequest assignObraSocialPacienteRequest) {

        log.info("Solicitud recibida: asignar plan {} a paciente {}",
                assignObraSocialPacienteRequest.planId(), assignObraSocialPacienteRequest.pacienteId());

        GetObraSocialPacienteResponse getObraSocialPacienteResponse = obraSocialPacienteApp.assignObraSocial(assignObraSocialPacienteRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(getObraSocialPacienteResponse);

    }

    /**
     * Desasigna una cobertura de obra social de un paciente (baja lógica de la cobertura).
     *
     * @param id {@code UUID} identificador de la cobertura
     * @return {@code ResponseEntity<SoftDeleteObraSocialPacienteResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<SoftDeleteObraSocialPacienteResponse> unassignObraSocial(@PathVariable UUID id) {

        log.info("Solicitud recibida: desasignar cobertura de obra social, id={}", id);

        SoftDeleteObraSocialPacienteResponse softDeleteObraSocialPacienteResponse = obraSocialPacienteApp.unassignObraSocial(id);

        return ResponseEntity.ok(softDeleteObraSocialPacienteResponse);

    }

    //endregion

}
