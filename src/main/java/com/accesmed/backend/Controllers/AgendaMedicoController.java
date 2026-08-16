package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.AgendaMedicoApp;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaMedicoCriteria;
import com.accesmed.backend.Records.AgendaMedico.Request.CreateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.UpdateAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Request.UpdateVigenciaAgendaMedicoRequest;
import com.accesmed.backend.Records.AgendaMedico.Response.CreateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.GetAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaHorarioResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.UpdateAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.UpdateVigenciaAgendaMedicoResponse;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para los endpoints de Agenda. Agrupa {@code AgendaMedico},
 * {@code AgendaDia} y {@code AgendaHorarios}: ninguna de las dos últimas tiene vida
 * independiente del período, así que no llevan controller propio. Recibe requests, delega
 * en el caso de uso {@code AgendaMedicoApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/AgendaMedico")
public class AgendaMedicoController {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaMedicoApp agendaMedicoApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un período de agenda nuevo a partir de un patrón semanal o de días sueltos.
     *
     * @param createAgendaMedicoRequest {@code CreateAgendaMedicoRequest} datos del período y del patrón
     * @return {@code ResponseEntity<CreateAgendaMedicoResponse>} la agenda creada (HTTP 201)
     */
    @PostMapping("/Agenda")
    public ResponseEntity<CreateAgendaMedicoResponse> createAgendaMedico(
            @Valid @RequestBody CreateAgendaMedicoRequest createAgendaMedicoRequest) {

        log.info("Solicitud recibida: crear agenda médica, médico={}", createAgendaMedicoRequest.medicoId());

        CreateAgendaMedicoResponse createAgendaMedicoResponse = agendaMedicoApp.createAgendaMedico(createAgendaMedicoRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createAgendaMedicoResponse);

    }

    /**
     * Aplica un delta de composición sobre los slots de una agenda (altas y bajas de días
     * y horarios).
     *
     * @param id {@code UUID} identificador de la agenda
     * @param updateAgendaMedicoRequest {@code UpdateAgendaMedicoRequest} delta a aplicar
     * @return {@code ResponseEntity<UpdateAgendaMedicoResponse>} los conteos de cada efecto aplicado (HTTP 200)
     */
    @PatchMapping("/Agenda/{id}")
    public ResponseEntity<UpdateAgendaMedicoResponse> updateAgendaMedico(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgendaMedicoRequest updateAgendaMedicoRequest) {

        log.info("Solicitud recibida: actualizar agenda médica, id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateAgendaMedicoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateAgendaMedicoRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UpdateAgendaMedicoResponse updateAgendaMedicoResponse = agendaMedicoApp.updateAgendaMedico(updateAgendaMedicoRequest);

        return ResponseEntity.ok(updateAgendaMedicoResponse);

    }

    /**
     * Actualiza el período de vigencia de una agenda (mover el inicio, adelantar o atrasar el fin).
     *
     * @param id {@code UUID} identificador de la agenda
     * @param updateVigenciaAgendaMedicoRequest {@code UpdateVigenciaAgendaMedicoRequest} nuevas fechas
     * @return {@code ResponseEntity<UpdateVigenciaAgendaMedicoResponse>} la vigencia actualizada (HTTP 200)
     */
    @PatchMapping("/Agenda/Vigencia/{id}")
    public ResponseEntity<UpdateVigenciaAgendaMedicoResponse> updateVigenciaAgendaMedico(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVigenciaAgendaMedicoRequest updateVigenciaAgendaMedicoRequest) {

        log.info("Solicitud recibida: actualizar vigencia de agenda médica, id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateVigenciaAgendaMedicoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateVigenciaAgendaMedicoRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
        }

        UpdateVigenciaAgendaMedicoResponse updateVigenciaAgendaMedicoResponse =
                agendaMedicoApp.updateVigenciaAgendaMedico(updateVigenciaAgendaMedicoRequest);

        return ResponseEntity.ok(updateVigenciaAgendaMedicoResponse);

    }

    /**
     * Lista agendas médicas con filtrado dinámico. Alimenta el selector de agendas del front.
     *
     * @param agendaMedicoCriteria {@code AgendaMedicoCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListAgendaMedicoResponse>>} página de agendas (HTTP 200)
     */
    @GetMapping("/Agenda")
    public ResponseEntity<PageResponse<ListAgendaMedicoResponse>> listAgendaMedico(
            AgendaMedicoCriteria agendaMedicoCriteria, Pageable pageable) {

        log.info("Solicitud recibida: listar agendas médicas, criteria={}, page={}", agendaMedicoCriteria, pageable);

        PageResponse<ListAgendaMedicoResponse> pageResponse = agendaMedicoApp.findAgendas(agendaMedicoCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Busca la única agenda médica que cumple el criteria proporcionado, con sus días y
     * horarios activos expandidos. Reemplazo de {@code GET /{id}} según
     * {@code Docs/FILTRADO-DINAMICO.md §3}.
     *
     * @param agendaMedicoCriteria {@code AgendaMedicoCriteria} filtros a aplicar
     * @return {@code ResponseEntity<GetAgendaMedicoResponse>} la agenda encontrada (HTTP 200)
     */
    @GetMapping("/Agenda/Buscar")
    public ResponseEntity<GetAgendaMedicoResponse> getAgendaMedico(AgendaMedicoCriteria agendaMedicoCriteria) {

        log.info("Solicitud recibida: buscar agenda médica, criteria={}", agendaMedicoCriteria);

        GetAgendaMedicoResponse getAgendaMedicoResponse = agendaMedicoApp.getAgendaMedico(agendaMedicoCriteria);

        return ResponseEntity.ok(getAgendaMedicoResponse);

    }

    /**
     * Lista los horarios de agenda del panel, con filtrado dinámico. Pinta el calendario
     * del período de agenda elegido.
     *
     * @param agendaHorariosCriteria {@code AgendaHorariosCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListAgendaHorarioResponse>>} página de horarios (HTTP 200)
     */
    @GetMapping("/Horarios")
    public ResponseEntity<PageResponse<ListAgendaHorarioResponse>> listHorariosAgenda(
            AgendaHorariosCriteria agendaHorariosCriteria, Pageable pageable) {

        log.info("Solicitud recibida: listar horarios de agenda, criteria={}, page={}", agendaHorariosCriteria, pageable);

        PageResponse<ListAgendaHorarioResponse> pageResponse = agendaMedicoApp.findHorariosAgenda(agendaHorariosCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Lista los horarios disponibles para reservar, el único listado que consume el
     * chatbot. Guardas fijas: sin ocupar, dentro del plazo de reserva y dentro del
     * horizonte de anticipación de la clínica.
     *
     * @param agendaHorariosCriteria {@code AgendaHorariosCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code ResponseEntity<PageResponse<ListHorarioDisponibleResponse>>} página de horarios disponibles (HTTP 200)
     */
    @GetMapping("/HorariosDisponibles")
    public ResponseEntity<PageResponse<ListHorarioDisponibleResponse>> listHorariosDisponibles(
            AgendaHorariosCriteria agendaHorariosCriteria, Pageable pageable) {

        log.info("Solicitud recibida: listar horarios disponibles, criteria={}, page={}", agendaHorariosCriteria, pageable);

        PageResponse<ListHorarioDisponibleResponse> pageResponse = agendaMedicoApp.findHorariosDisponibles(agendaHorariosCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
