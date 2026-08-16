package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.MedicoPrestacionApp;
import com.accesmed.backend.Records.MedicoPrestacion.Request.AssignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Request.UnassignMedicoPrestacionRequest;
import com.accesmed.backend.Records.MedicoPrestacion.Response.GetMedicoPrestacionResponse;
import com.accesmed.backend.Records.MedicoPrestacion.Response.UnassignMedicoPrestacionResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * Desasigna una prestación de un médico: cierra el período de vigencia de la
     * asignación (no la borra).
     *
     * @param id {@code UUID} identificador de la asignación
     * @param unassignMedicoPrestacionRequest {@code UnassignMedicoPrestacionRequest} fecha de corte
     * @return {@code ResponseEntity<UnassignMedicoPrestacionResponse>} la confirmación del cierre de
     *         vigencia (HTTP 200)
     */
    @PatchMapping("/Vigencia/{id}")
    public ResponseEntity<UnassignMedicoPrestacionResponse> unassignPrestacion(
            @PathVariable UUID id,
            @Valid @RequestBody UnassignMedicoPrestacionRequest unassignMedicoPrestacionRequest) {

        log.info("Solicitud recibida: desasignar prestación, id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(unassignMedicoPrestacionRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, unassignMedicoPrestacionRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UnassignMedicoPrestacionResponse unassignMedicoPrestacionResponse =
                medicoPrestacionApp.unassignPrestacion(unassignMedicoPrestacionRequest);

        return ResponseEntity.ok(unassignMedicoPrestacionResponse);

    }

    //endregion

}
