package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.MedicoPrestacionApp;
import com.accesmed.backend.Records.MedicoPrestacion.Request.AssignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Response.GetMedicoPrestacionResponse;
import com.accesmed.backend.Records.MedicoPrestacion.Response.SoftDeleteMedicoPrestacionResponse;
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
 * Controlador REST para los endpoints de Médico-Prestación. No es CRUD completo: solo
 * asigna y desasigna la prestación de un médico. Recibe requests, delega en el caso de
 * uso {@code MedicoPrestacionApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/MedicoPrestacion")
public class MedicoPrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoPrestacionApp medicoPrestacionApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Asigna una prestación existente a un médico existente.
     *
     * @param assignMedicoPrestacionRequest {@code AssignMedicoPrestacionRequest} datos de la asignación
     * @return {@code ResponseEntity<GetMedicoPrestacionResponse>} la asignación creada (HTTP 201)
     */
    @PostMapping("/Asignar")
    public ResponseEntity<GetMedicoPrestacionResponse> assignPrestacion(
            @Valid @RequestBody AssignMedicoPrestacionRequest assignMedicoPrestacionRequest) {

        log.info("Solicitud recibida: asignar prestación {} a médico {}",
                assignMedicoPrestacionRequest.prestacionId(), assignMedicoPrestacionRequest.medicoId());

        GetMedicoPrestacionResponse getMedicoPrestacionResponse = medicoPrestacionApp.assignPrestacion(assignMedicoPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(getMedicoPrestacionResponse);

    }

    /**
     * Desasigna una prestación de un médico (baja lógica de la asignación).
     *
     * @param id {@code UUID} identificador de la asignación
     * @return {@code ResponseEntity<SoftDeleteMedicoPrestacionResponse>} la confirmación de la baja (HTTP 200)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<SoftDeleteMedicoPrestacionResponse> unassignPrestacion(@PathVariable UUID id) {

        log.info("Solicitud recibida: desasignar prestación, id={}", id);

        SoftDeleteMedicoPrestacionResponse softDeleteMedicoPrestacionResponse = medicoPrestacionApp.unassignPrestacion(id);

        return ResponseEntity.ok(softDeleteMedicoPrestacionResponse);

    }

    //endregion

}
