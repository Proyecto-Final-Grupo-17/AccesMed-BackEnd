package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.ClinicaApp;
import com.accesmed.backend.Records.Clinica.Request.UpdateClinicaRequest;
import com.accesmed.backend.Records.Clinica.Response.GetClinicaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Controlador REST para los endpoints de Clínica. Al ser una instancia única, sin alta
 * ni baja, no lleva id de ruta: recibe requests, delega en el caso de uso
 * {@code ClinicaApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Clinica")
public class ClinicaController {

    //region ========== Dependencias o inyecciones ==========

    private final ClinicaApp clinicaApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Busca la instancia única de clínica.
     *
     * @return {@code ResponseEntity<GetClinicaResponse>} la clínica (HTTP 200)
     */
    @PreAuthorize("hasAuthority('CONFIG_CONSULTAR')")
    @GetMapping("/Clinica")
    public ResponseEntity<GetClinicaResponse> findClinica() {

        log.info("Solicitud recibida: buscar clínica");

        GetClinicaResponse getClinicaResponse = clinicaApp.findClinica();

        return ResponseEntity.ok(getClinicaResponse);

    }

    /**
     * Actualiza la instancia única de clínica.
     *
     * @param updateClinicaRequest {@code UpdateClinicaRequest} datos a actualizar
     * @return {@code ResponseEntity<GetClinicaResponse>} la clínica actualizada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('CONFIG_MODIFICAR')")
    @PatchMapping("/Clinica")
    public ResponseEntity<GetClinicaResponse> updateClinica(
            @Valid @RequestBody UpdateClinicaRequest updateClinicaRequest) {

        log.info("Solicitud recibida: actualizar clínica");

        GetClinicaResponse getClinicaResponse = clinicaApp.updateClinica(updateClinicaRequest);

        return ResponseEntity.ok(getClinicaResponse);

    }

    //endregion

}
