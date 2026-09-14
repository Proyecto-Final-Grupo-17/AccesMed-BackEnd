package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.TurnoApp;
import com.accesmed.backend.Records.Turno.Criteria.TurnoCriteria;
import com.accesmed.backend.Records.Turno.Request.CancelTurnoRequest;
import com.accesmed.backend.Records.Turno.Request.CreateTurnoRequest;
import com.accesmed.backend.Records.Turno.Request.ReprogramTurnoRequest;
import com.accesmed.backend.Records.Turno.Request.ValidateTurnoRequest;
import com.accesmed.backend.Records.Turno.Response.CancelTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ConfirmTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.CreateTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.FinishTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ListTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ReprogramTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.StartAtencionTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.StartSalaDeEsperaTurnoResponse;
import com.accesmed.backend.Records.Turno.Response.ValidateTurnoResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import com.accesmed.backend.Services.QueryServices.TurnoQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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

import java.util.UUID;

/**
 * Controller REST para la entidad Turno. Expone los endpoints de creación, reprogramación,
 * cancelación, validación y listado de turnos.
 * Nota: El endpoint DELETE para cancelar transiciona el turno a estado CANCELADO
 * (no lo borra físicamente), siguiendo la semántica de baja lógica de estado del sistema.
 */
@Slf4j
@RestController
@RequestMapping("/accesmed-api/Turno")
@RequiredArgsConstructor
public class TurnoController {

    //region ========== Dependencias o inyecciones ==========

    private final TurnoApp turnoApp;
    private final TurnoQueryService turnoQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un turno nuevo para un paciente en un slot disponible de un médico y prestación.
     *
     * @param createTurnoRequest {@code CreateTurnoRequest} datos del turno a crear
     * @return {@code ResponseEntity<CreateTurnoResponse>} el turno creado con status 201
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si alguno de
     *         los recursos no existe o no está disponible (422 via handler)
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si alguna regla de
     *         negocio es incumplida (409 via handler)
     */
    @PostMapping("/Turno")
    public ResponseEntity<CreateTurnoResponse> createTurno(
            @Valid @RequestBody CreateTurnoRequest createTurnoRequest) {

        log.info("Solicitud recibida: crear turno para paciente id={}", createTurnoRequest.pacienteId());

        CreateTurnoResponse createTurnoResponse = turnoApp.createTurno(createTurnoRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createTurnoResponse);

    }

    /**
     * Reprograma un turno existente en un nuevo slot, creando un turno nuevo enlazado
     * por {@code turnoOrigen} y marcando el original como REPROGRAMADO.
     *
     * @param id {@code UUID} identificador del turno a reprogramar (validado contra el request)
     * @param reprogramTurnoRequest {@code ReprogramTurnoRequest} nuevo slot y id del turno
     * @return {@code ResponseEntity<ReprogramTurnoResponse>} el turno nuevo con status 200
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno o slot no existen
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el plazo venció o hay otra regla incumplida
     */
    @PatchMapping("/Turno/{id}/Reprogramacion")
    public ResponseEntity<ReprogramTurnoResponse> reprogramTurno(
            @PathVariable UUID id,
            @Valid @RequestBody ReprogramTurnoRequest reprogramTurnoRequest) {

        log.info("Solicitud recibida: reprogramar turno id={}", id);

        //Validar que el id de la ruta coincida con el del body
        if (!id.equals(reprogramTurnoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, reprogramTurnoRequest.id());
            throw new ValidacionException(getClass(),
                    java.util.List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        ReprogramTurnoResponse reprogramTurnoResponse = turnoApp.reprogramTurno(reprogramTurnoRequest);

        return ResponseEntity.ok(reprogramTurnoResponse);

    }

    /**
     * Cancela un turno existente, liberando su slot y transicionándolo a estado CANCELADO.
     * Nota: Este endpoint usa DELETE pero no borra el registro (baja lógica de estado).
     *
     * @param id {@code UUID} identificador del turno a cancelar
     * @return {@code ResponseEntity<Void>} sin contenido con status 204
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el turno está en estado final
     */
    @DeleteMapping("/Turno/{id}")
    public ResponseEntity<Void> cancelTurno(@PathVariable UUID id) {

        log.info("Solicitud recibida: cancelar turno id={}", id);

        var cancelTurnoRequest = new CancelTurnoRequest(id, null);
        turnoApp.cancelTurno(cancelTurnoRequest);

        return ResponseEntity.noContent().build();

    }

    /**
     * Valida (aprueba o rechaza) un turno en estado ESPERA_VALIDACION.
     *
     * @param id {@code UUID} identificador del turno a validar (validado contra el request)
     * @param validateTurnoRequest {@code ValidateTurnoRequest} id y aprobado (boolean)
     * @return {@code ResponseEntity<ValidateTurnoResponse>} el turno con su nuevo estado y status 200
     * @throws ValidacionException {@code ValidacionException} si el id de la ruta no coincide con el del body
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el turno no está en ESPERA_VALIDACION
     */
    @PatchMapping("/Turno/{id}/Validacion")
    public ResponseEntity<ValidateTurnoResponse> validateTurno(
            @PathVariable UUID id,
            @Valid @RequestBody ValidateTurnoRequest validateTurnoRequest) {

        log.info("Solicitud recibida: validar turno id={}, aprobado={}", id, validateTurnoRequest.aprobado());

        //Validar que el id de la ruta coincida con el del body
        if (!id.equals(validateTurnoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, validateTurnoRequest.id());
            throw new ValidacionException(getClass(),
                    java.util.List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        ValidateTurnoResponse validateTurnoResponse = turnoApp.validateTurno(validateTurnoRequest);

        return ResponseEntity.ok(validateTurnoResponse);

    }

    /**
     * Confirma un turno en estado PENDIENTE, transicionándolo a estado CONFIRMADO.
     *
     * @param id {@code UUID} identificador del turno a confirmar
     * @return {@code ResponseEntity<ConfirmTurnoResponse>} el turno confirmado con status 200
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el turno no está en PENDIENTE
     */
    @PatchMapping("/Turno/{id}/Confirmacion")
    public ResponseEntity<ConfirmTurnoResponse> confirmTurno(@PathVariable UUID id) {

        log.info("Solicitud recibida: confirmar turno id={}", id);

        return ResponseEntity.ok(turnoApp.confirmTurno(id));

    }

    /**
     * Inicia la sala de espera para un turno en estado CONFIRMADO, transicionándolo
     * a estado EN_SALA_DE_ESPERA.
     *
     * @param id {@code UUID} identificador del turno
     * @return {@code ResponseEntity<StartSalaDeEsperaTurnoResponse>} el turno en sala de espera con status 200
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el turno no está en CONFIRMADO
     */
    @PatchMapping("/Turno/{id}/SalaDeEspera")
    public ResponseEntity<StartSalaDeEsperaTurnoResponse> startSalaDeEsperaTurno(@PathVariable UUID id) {

        log.info("Solicitud recibida: iniciar sala de espera para turno id={}", id);

        return ResponseEntity.ok(turnoApp.startSalaDeEsperaTurno(id));

    }

    /**
     * Inicia la atención de un turno en estado EN_SALA_DE_ESPERA, transicionándolo
     * a estado EN_CURSO.
     *
     * @param id {@code UUID} identificador del turno
     * @return {@code ResponseEntity<StartAtencionTurnoResponse>} el turno en atención con status 200
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el turno no está en EN_SALA_DE_ESPERA
     */
    @PatchMapping("/Turno/{id}/Atencion")
    public ResponseEntity<StartAtencionTurnoResponse> startAtencionTurno(@PathVariable UUID id) {

        log.info("Solicitud recibida: iniciar atención para turno id={}", id);

        return ResponseEntity.ok(turnoApp.startAtencionTurno(id));

    }

    /**
     * Finaliza un turno en estado EN_CURSO, transicionándolo a estado FINALIZADO.
     *
     * @param id {@code UUID} identificador del turno
     * @return {@code ResponseEntity<FinishTurnoResponse>} el turno finalizado con status 200
     * @throws com.accesmed.backend.Services.Errors.RecursoNoEncontradoException si el turno no existe
     * @throws com.accesmed.backend.Services.Errors.ReglaNegocioException si el turno no está en EN_CURSO
     */
    @PatchMapping("/Turno/{id}/Finalizacion")
    public ResponseEntity<FinishTurnoResponse> finishTurno(@PathVariable UUID id) {

        log.info("Solicitud recibida: finalizar turno id={}", id);

        return ResponseEntity.ok(turnoApp.finishTurno(id));

    }

    /**
     * Lista turnos con filtrado dinámico por criterios y paginación.
     *
     * @param criteria {@code TurnoCriteria} filtros a aplicar (todos opcionales)
     * @param pageable {@code Pageable} paginación y ordenamiento
     * @return {@code ResponseEntity<PageResponse<ListTurnoResponse>>} página de turnos y status 200
     */
    @GetMapping("/Turno")
    public ResponseEntity<PageResponse<ListTurnoResponse>> listTurnos(
            TurnoCriteria criteria,
            Pageable pageable) {

        log.info("Solicitud recibida: listar turnos con criteria={}, pageable={}", criteria, pageable);

        PageResponse<ListTurnoResponse> pageResponse = turnoQueryService.findTurnos(criteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
