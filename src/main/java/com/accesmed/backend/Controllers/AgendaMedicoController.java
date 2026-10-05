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
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.QueryServices.AgendaHorariosDiaQueryService;
import com.accesmed.backend.Services.QueryServices.AgendaMedicoQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Controlador REST para los endpoints de Agenda. Agrupa {@code AgendaMedico} y
 * {@code AgendaHorariosDia}: esta última no tiene vida independiente del período, así que
 * no lleva controller propio. Recibe requests, delega en el caso de uso
 * {@code AgendaMedicoApp} y devuelve responses.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/AgendaMedico")
public class AgendaMedicoController {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaMedicoApp agendaMedicoApp;
    private final AgendaMedicoQueryService agendaMedicoQueryService;
    private final AgendaHorariosDiaQueryService agendaHorariosDiaQueryService;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un período de agenda nuevo a partir de un patrón semanal o de días sueltos.
     *
     * @param createAgendaMedicoRequest {@code CreateAgendaMedicoRequest} datos del período y del patrón
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<CreateAgendaMedicoResponse>} la agenda creada (HTTP 201)
     */
    @PreAuthorize("hasAuthority('AGEN_CONFIGURAR')")
    @PostMapping("/Agenda")
    public ResponseEntity<CreateAgendaMedicoResponse> createAgendaMedico(
            @Valid @RequestBody CreateAgendaMedicoRequest createAgendaMedicoRequest,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: crear agenda médica, médico={}", createAgendaMedicoRequest.medicoId());

        CreateAgendaMedicoResponse createAgendaMedicoResponse = agendaMedicoApp.createAgendaMedico(createAgendaMedicoRequest, usuarioDetails);

        return ResponseEntity.status(HttpStatus.CREATED).body(createAgendaMedicoResponse);

    }

    /**
     * Aplica un delta de composición sobre los slots de una agenda (altas y bajas de días
     * y horarios).
     *
     * @param id {@code UUID} identificador de la agenda
     * @param updateAgendaMedicoRequest {@code UpdateAgendaMedicoRequest} delta a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<UpdateAgendaMedicoResponse>} los conteos de cada efecto aplicado (HTTP 200)
     */
    @PreAuthorize("hasAuthority('AGEN_CONFIGURAR')")
    @PatchMapping("/Agenda/{id}")
    public ResponseEntity<UpdateAgendaMedicoResponse> updateAgendaMedico(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgendaMedicoRequest updateAgendaMedicoRequest,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: actualizar agenda médica, id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateAgendaMedicoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateAgendaMedicoRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        UpdateAgendaMedicoResponse updateAgendaMedicoResponse = agendaMedicoApp.updateAgendaMedico(updateAgendaMedicoRequest, usuarioDetails);

        return ResponseEntity.ok(updateAgendaMedicoResponse);

    }

    /**
     * Actualiza el período de vigencia de una agenda (mover el inicio, adelantar o atrasar el fin).
     *
     * @param id {@code UUID} identificador de la agenda
     * @param updateVigenciaAgendaMedicoRequest {@code UpdateVigenciaAgendaMedicoRequest} nuevas fechas
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<UpdateVigenciaAgendaMedicoResponse>} la vigencia actualizada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('AGEN_CONFIGURAR')")
    @PatchMapping("/Agenda/Vigencia/{id}")
    public ResponseEntity<UpdateVigenciaAgendaMedicoResponse> updateVigenciaAgendaMedico(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVigenciaAgendaMedicoRequest updateVigenciaAgendaMedicoRequest,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: actualizar vigencia de agenda médica, id={}", id);

        //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
        if (!id.equals(updateVigenciaAgendaMedicoRequest.id())) {
            log.warn("Id de ruta ({}) distinto al del body ({})", id, updateVigenciaAgendaMedicoRequest.id());
            throw new ValidacionException(getClass(),
                    List.of("El identificador indicado en la dirección no coincide con el de los datos enviados."));
        }

        UpdateVigenciaAgendaMedicoResponse updateVigenciaAgendaMedicoResponse =
                agendaMedicoApp.updateVigenciaAgendaMedico(updateVigenciaAgendaMedicoRequest, usuarioDetails);

        return ResponseEntity.ok(updateVigenciaAgendaMedicoResponse);

    }

    /**
     * Lista agendas médicas con filtrado dinámico. Alimenta el selector de agendas del front.
     *
     * @param agendaMedicoCriteria {@code AgendaMedicoCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListAgendaMedicoResponse>>} página de agendas (HTTP 200)
     */
    @PreAuthorize("hasAuthority('AGEN_CONSULTAR')")
    @GetMapping("/Agenda")
    public ResponseEntity<PageResponse<ListAgendaMedicoResponse>> listAgendaMedico(
            AgendaMedicoCriteria agendaMedicoCriteria, Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar agendas médicas, criteria={}, page={}", agendaMedicoCriteria, pageable);

        PageResponse<ListAgendaMedicoResponse> pageResponse = agendaMedicoQueryService.findAgendasMedicas(agendaMedicoCriteria, pageable, usuarioDetails);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Busca la única agenda médica que cumple el criteria proporcionado, con sus días y
     * horarios activos expandidos. Reemplazo de {@code GET /{id}} según
     * {@code Docs/FILTRADO-DINAMICO.md §3}.
     *
     * @param agendaMedicoCriteria {@code AgendaMedicoCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<GetAgendaMedicoResponse>} la agenda encontrada (HTTP 200)
     */
    @PreAuthorize("hasAuthority('AGEN_CONSULTAR')")
    @GetMapping("/Agenda/Buscar")
    public ResponseEntity<GetAgendaMedicoResponse> getAgendaMedico(AgendaMedicoCriteria agendaMedicoCriteria,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: buscar agenda médica, criteria={}", agendaMedicoCriteria);

        GetAgendaMedicoResponse getAgendaMedicoResponse = agendaMedicoQueryService.findAgendaMedicoByCriteria(agendaMedicoCriteria, usuarioDetails);

        return ResponseEntity.ok(getAgendaMedicoResponse);

    }

    /**
     * Lista los horarios de agenda del panel, con filtrado dinámico. Pinta el calendario
     * del período de agenda elegido.
     *
     * @param agendaHorariosCriteria {@code AgendaHorariosCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListAgendaHorarioResponse>>} página de horarios (HTTP 200)
     */
    @PreAuthorize("hasAuthority('AGEN_CONSULTAR')")
    @GetMapping("/Horarios")
    public ResponseEntity<PageResponse<ListAgendaHorarioResponse>> listHorariosAgenda(
            AgendaHorariosCriteria agendaHorariosCriteria, Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar horarios de agenda, criteria={}, page={}", agendaHorariosCriteria, pageable);

        PageResponse<ListAgendaHorarioResponse> pageResponse = agendaHorariosDiaQueryService.findHorariosByCriteria(agendaHorariosCriteria, pageable, usuarioDetails);

        return ResponseEntity.ok(pageResponse);

    }

    /**
     * Lista los horarios disponibles para reservar, el único listado que consume el
     * chatbot. Guardas fijas: sin ocupar, dentro del plazo de reserva y dentro del
     * horizonte de anticipación de la clínica.
     *
     * @param agendaHorariosCriteria {@code AgendaHorariosCriteria} filtros a aplicar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code ResponseEntity<PageResponse<ListHorarioDisponibleResponse>>} página de horarios disponibles (HTTP 200)
     */
    @PreAuthorize("hasAuthority('AGEN_CONSULTAR')")
    @GetMapping("/HorariosDisponibles")
    public ResponseEntity<PageResponse<ListHorarioDisponibleResponse>> listHorariosDisponibles(
            AgendaHorariosCriteria agendaHorariosCriteria, Pageable pageable,
            @AuthenticationPrincipal UsuarioDetails usuarioDetails) {

        log.info("Solicitud recibida: listar horarios disponibles, criteria={}, page={}", agendaHorariosCriteria, pageable);

        PageResponse<ListHorarioDisponibleResponse> pageResponse = agendaHorariosDiaQueryService.findHorariosDisponibles(agendaHorariosCriteria, pageable);

        return ResponseEntity.ok(pageResponse);

    }

    //endregion

}
