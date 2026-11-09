package com.accesmed.backend.Controllers;

import com.accesmed.backend.Application.AgenteApp;
import com.accesmed.backend.Domain.TipoCobertura;
import com.accesmed.backend.Records.Agente.Request.AccionTurnoAgente;
import com.accesmed.backend.Records.Agente.Request.CreatePacienteAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.CreateTurnoAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.FranjaHorariaAgente;
import com.accesmed.backend.Records.Agente.Request.ReprogramTurnoAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.SearchHorarioAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.VerifyRespuestaAgenteRequest;
import com.accesmed.backend.Records.Agente.Request.UpdatePacienteAgenteRequest;
import com.accesmed.backend.Records.Agente.Response.ConfirmTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreatePacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.CreateTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetClinicaAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetCoberturaPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetCotizacionTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetOfertaEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.GetPacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListHorarioDisponibleAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListObraSocialAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ReprogramTurnoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchHorarioAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.UpdatePacienteAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.VerifyRespuestaAgenteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST del canal chatbot: los endpoints que consumen como Custom Tools los agentes de
 * Flowise (actor Paciente). Se autentican con la API key del agente ({@code X-Agente-Api-Key}) y
 * identifican al paciente por el header {@code X-Paciente-Telefono}, que inyecta el pasamanos de
 * Chatwoot y que el LLM no compone. Toda la lógica vive en {@link AgenteApp}.
 */
@Slf4j
@RestController
@RequestMapping("/accesmed-api/Agente")
@RequiredArgsConstructor
public class AgenteController {

    //region ========== Dependencias o inyecciones ==========

    private final AgenteApp agenteApp;

    //endregion

    //region ========== Constantes ==========

    private static final String HEADER_TELEFONO = "X-Paciente-Telefono";

    //endregion

    //region ========== Paciente ==========

    /**
     * Devuelve los datos del paciente identificado por el teléfono del canal.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @return {@code ResponseEntity<GetPacienteAgenteResponse>} el paciente con status 200
     */
    @GetMapping("/Paciente")
    public ResponseEntity<GetPacienteAgenteResponse> getPaciente(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono) {

        log.info("Solicitud recibida: consultar paciente del canal");

        GetPacienteAgenteResponse getPacienteAgenteResponse = agenteApp.getPaciente(numeroTelefono);

        return ResponseEntity.ok(getPacienteAgenteResponse);

    }

    /**
     * Registra un paciente nuevo con el teléfono verificado del canal (ECU-PAC-1).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param createPacienteAgenteRequest {@code CreatePacienteAgenteRequest} datos del paciente
     * @return {@code ResponseEntity<CreatePacienteAgenteResponse>} la confirmación con status 201
     */
    @PostMapping("/Paciente")
    public ResponseEntity<CreatePacienteAgenteResponse> createPaciente(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @Valid @RequestBody CreatePacienteAgenteRequest createPacienteAgenteRequest) {

        log.info("Solicitud recibida: registrar paciente desde el canal");

        CreatePacienteAgenteResponse createPacienteAgenteResponse =
                agenteApp.createPaciente(numeroTelefono, createPacienteAgenteRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createPacienteAgenteResponse);

    }

    /**
     * Modifica los datos del paciente identificado por el teléfono del canal (ECU-PAC-2).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param updatePacienteAgenteRequest {@code UpdatePacienteAgenteRequest} datos a modificar
     * @return {@code ResponseEntity<UpdatePacienteAgenteResponse>} los datos actualizados con status 200
     */
    @PatchMapping("/Paciente")
    public ResponseEntity<UpdatePacienteAgenteResponse> updatePaciente(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @Valid @RequestBody UpdatePacienteAgenteRequest updatePacienteAgenteRequest) {

        log.info("Solicitud recibida: modificar paciente desde el canal");

        UpdatePacienteAgenteResponse updatePacienteAgenteResponse =
                agenteApp.updatePaciente(numeroTelefono, updatePacienteAgenteRequest);

        return ResponseEntity.ok(updatePacienteAgenteResponse);

    }

    /**
     * Da de baja al paciente identificado por el teléfono del canal (ECU-PACIENTE-3).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @return {@code ResponseEntity<Void>} sin contenido con status 204
     */
    @DeleteMapping("/Paciente")
    public ResponseEntity<Void> softDeletePaciente(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono) {

        log.info("Solicitud recibida: dar de baja al paciente del canal");

        agenteApp.softDeletePaciente(numeroTelefono);

        return ResponseEntity.noContent().build();

    }

    //endregion

    //region ========== Verificación ==========

    /**
     * Verifica contra la base las afirmaciones de una respuesta del agente antes de mandarla al
     * paciente (Diseno-Chatbot §4.3).
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para turnos y cotizaciones)
     * @param verifyRespuestaAgenteRequest {@code VerifyRespuestaAgenteRequest} afirmaciones agrupadas por entidad
     * @return {@code ResponseEntity<VerifyRespuestaAgenteResponse>} el veredicto por ítem con status 200
     */
    @PostMapping("/Verificacion")
    public ResponseEntity<VerifyRespuestaAgenteResponse> verifyRespuestaAgente(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestBody VerifyRespuestaAgenteRequest verifyRespuestaAgenteRequest) {

        log.info("Solicitud recibida: verificar respuesta del agente");

        VerifyRespuestaAgenteResponse verifyRespuestaAgenteResponse =
                agenteApp.verifyRespuestaAgente(numeroTelefono, verifyRespuestaAgenteRequest);

        return ResponseEntity.ok(verifyRespuestaAgenteResponse);

    }

    //endregion

    //region ========== Información general ==========

    /**
     * Devuelve la información general de la clínica (ECU-TURN-1).
     *
     * @return {@code ResponseEntity<GetClinicaAgenteResponse>} la clínica con status 200
     */
    @GetMapping("/Clinica")
    public ResponseEntity<GetClinicaAgenteResponse> getClinica() {

        log.info("Solicitud recibida: consultar información de la clínica");

        GetClinicaAgenteResponse getClinicaAgenteResponse = agenteApp.getClinica();

        return ResponseEntity.ok(getClinicaAgenteResponse);

    }

    /**
     * Lista las especialidades activas (ECU-TURN-1).
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para el aviso de espera)
     * @return {@code ResponseEntity<List<ListEspecialidadAgenteResponse>>} las especialidades con status 200
     */
    @GetMapping("/Especialidad")
    public ResponseEntity<List<ListEspecialidadAgenteResponse>> listEspecialidades(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono) {

        log.info("Solicitud recibida: listar especialidades");

        List<ListEspecialidadAgenteResponse> listEspecialidadAgenteResponses = agenteApp.listEspecialidades(numeroTelefono);

        return ResponseEntity.ok(listEspecialidadAgenteResponses);

    }

    /**
     * Lista las obras sociales activas con los planes que el paciente puede declarar como
     * cobertura (ECU-PAC-1 y ECU-PAC-2).
     *
     * @return {@code ResponseEntity<List<ListObraSocialAgenteResponse>>} las obras sociales con status 200
     */
    @GetMapping("/ObraSocial")
    public ResponseEntity<List<ListObraSocialAgenteResponse>> listObrasSociales() {

        log.info("Solicitud recibida: listar obras sociales");

        List<ListObraSocialAgenteResponse> listObraSocialAgenteResponses = agenteApp.listObrasSociales();

        return ResponseEntity.ok(listObraSocialAgenteResponses);

    }

    /**
     * Devuelve la oferta de una especialidad (resuelta por parecido con el nombre): sus prestaciones
     * publicadas, cada una con los médicos que la atienden (ECU-TURN-1).
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para la tanda)
     * @param nombre {@code String} nombre aproximado de la especialidad
     * @return {@code ResponseEntity<GetOfertaEspecialidadAgenteResponse>} la oferta con status 200
     */
    @GetMapping("/Especialidad/{nombre}/Oferta")
    public ResponseEntity<GetOfertaEspecialidadAgenteResponse> getOfertaEspecialidad(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @PathVariable String nombre) {

        log.info("Solicitud recibida: consultar oferta de la especialidad {}", nombre);

        GetOfertaEspecialidadAgenteResponse getOfertaEspecialidadAgenteResponse = agenteApp.getOfertaEspecialidad(numeroTelefono, nombre);

        return ResponseEntity.ok(getOfertaEspecialidadAgenteResponse);

    }

    /**
     * Busca prestaciones publicadas por parecido con el nombre ("¿hacen electrocardiogramas?"),
     * con su especialidad, indicaciones y médicos (ECU-TURN-1).
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para la tanda)
     * @param nombre {@code String} nombre aproximado de la prestación
     * @return {@code ResponseEntity<List<SearchPrestacionAgenteResponse>>} las coincidencias con status 200 (vacía si no hay)
     */
    @GetMapping("/Prestacion")
    public ResponseEntity<List<SearchPrestacionAgenteResponse>> searchPrestaciones(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestParam String nombre) {

        log.info("Solicitud recibida: buscar prestaciones parecidas a {}", nombre);

        List<SearchPrestacionAgenteResponse> searchPrestacionAgenteResponses = agenteApp.searchPrestaciones(numeroTelefono, nombre);

        return ResponseEntity.ok(searchPrestacionAgenteResponses);

    }

    /**
     * Busca médicos activos por parecido con su nombre o apellido ("turno con Raúl"), por especialidad o
     * por prestación (al menos uno de los tres), con su especialidad, las prestaciones que atienden, si
     * hacen la prestación pedida y los días con agenda en las próximas semanas (ECU-TURN-1).
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para la tanda)
     * @param nombre {@code String} nombre o apellido aproximado del médico (opcional)
     * @param especialidad {@code String} nombre aproximado de la especialidad (opcional)
     * @param prestacion {@code String} nombre aproximado de la prestación (opcional)
     * @return {@code ResponseEntity<List<SearchMedicoAgenteResponse>>} las coincidencias con status 200 (vacía si no hay)
     */
    @GetMapping("/Medico")
    public ResponseEntity<List<SearchMedicoAgenteResponse>> searchMedicos(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) String prestacion) {

        log.info("Solicitud recibida: buscar médicos (nombre={}, especialidad={}, prestación={})", nombre, especialidad, prestacion);

        List<SearchMedicoAgenteResponse> searchMedicoAgenteResponses =
                agenteApp.searchMedicos(numeroTelefono, nombre, especialidad, prestacion);

        return ResponseEntity.ok(searchMedicoAgenteResponses);

    }

    /**
     * Consulta qué cubre una obra social y plan para una prestación y cuánto se pagaría, sin elegir un
     * horario. Sin obra social ni plan usa la cobertura del paciente registrado.
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}; hace falta si no se nombra obra social)
     * @param prestacion {@code String} nombre aproximado de la prestación
     * @param obraSocial {@code String} obra social dicha por el paciente (opcional)
     * @param plan {@code String} plan dicho por el paciente (opcional)
     * @param medico {@code String} matrícula o nombre del médico (opcional; sin él, el monto de cada médico que la hace)
     * @return {@code ResponseEntity<GetCoberturaPrestacionAgenteResponse>} la cobertura y los montos con status 200
     */
    @GetMapping("/Prestacion/Cobertura")
    public ResponseEntity<GetCoberturaPrestacionAgenteResponse> getCoberturaPrestacion(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestParam String prestacion,
            @RequestParam(required = false) String obraSocial,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String medico) {

        log.info("Solicitud recibida: consultar cobertura de la prestación {}", prestacion);

        GetCoberturaPrestacionAgenteResponse getCoberturaPrestacionAgenteResponse =
                agenteApp.getCoberturaPrestacion(numeroTelefono, prestacion, obraSocial, plan, medico);

        return ResponseEntity.ok(getCoberturaPrestacionAgenteResponse);

    }

    //endregion

    //region ========== Turnos ==========

    /**
     * Busca horarios disponibles con filtros flexibles (médico, prestación, especialidad, día, franja y
     * hora), agrupados por día, médico y prestación (ECU-TURN-2). Hace falta al menos uno de médico,
     * prestación o especialidad.
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para la tanda)
     * @param searchHorarioAgenteRequest {@code SearchHorarioAgenteRequest} filtros, como parámetros de la URL
     * @return {@code ResponseEntity<SearchHorarioAgenteResponse>} los horarios agrupados (o el motivo por el que no hay) con status 200
     */
    @GetMapping("/Horario")
    public ResponseEntity<SearchHorarioAgenteResponse> searchHorarios(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            SearchHorarioAgenteRequest searchHorarioAgenteRequest) {

        log.info("Solicitud recibida: buscar horarios disponibles");

        SearchHorarioAgenteResponse searchHorarioAgenteResponse = agenteApp.searchHorarios(numeroTelefono, searchHorarioAgenteRequest);

        return ResponseEntity.ok(searchHorarioAgenteResponse);

    }

    /**
     * Lista los horarios disponibles de un médico para una prestación (ECU-TURN-2).
     *
     * @param numeroTelefono {@code String} teléfono del canal (header {@code X-Paciente-Telefono}, opcional: solo para la tanda)
     * @param nombrePrestacion {@code String} nombre aproximado de la prestación
     * @param matricula {@code String} matrícula del médico
     * @param franjaHoraria {@code FranjaHorariaAgente} franja del día (opcional)
     * @param fecha {@code LocalDate} día puntual, {@code AAAA-MM-DD} (opcional)
     * @param pageable {@code Pageable} paginación
     * @return {@code ResponseEntity<List<ListHorarioDisponibleAgenteResponse>>} los horarios con status 200
     */
    @GetMapping("/Turno/Disponibilidad")
    public ResponseEntity<List<ListHorarioDisponibleAgenteResponse>> listHorariosDisponibles(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestParam String nombrePrestacion,
            @RequestParam String matricula,
            @RequestParam(required = false) FranjaHorariaAgente franjaHoraria,
            @RequestParam(required = false) LocalDate fecha,
            @PageableDefault(size = 10, sort = {"fecha", "horaDesde"}) Pageable pageable) {

        log.info("Solicitud recibida: consultar horarios disponibles de la prestación {}", nombrePrestacion);

        List<ListHorarioDisponibleAgenteResponse> listHorarioDisponibleAgenteResponses =
                agenteApp.listHorariosDisponibles(numeroTelefono, nombrePrestacion, matricula, franjaHoraria, fecha, pageable);

        return ResponseEntity.ok(listHorarioDisponibleAgenteResponses);

    }

    /**
     * Cotiza un turno antes de reservarlo o reprogramarlo: cobertura, monto e indicaciones (Diseno-Chatbot §4.4).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param slotId {@code UUID} horario elegido
     * @param cobertura {@code TipoCobertura} cobertura pedida (obligatoria para un turno nuevo)
     * @param codigoTurno {@code String} código del turno a reprogramar (opcional)
     * @return {@code ResponseEntity<GetCotizacionTurnoAgenteResponse>} la cotización con status 200
     */
    @GetMapping("/Turno/Cotizacion")
    public ResponseEntity<GetCotizacionTurnoAgenteResponse> getCotizacionTurno(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestParam UUID slotId,
            @RequestParam(required = false) TipoCobertura cobertura,
            @RequestParam(required = false) String codigoTurno) {

        log.info("Solicitud recibida: cotizar turno desde el canal");

        GetCotizacionTurnoAgenteResponse getCotizacionTurnoAgenteResponse =
                agenteApp.getCotizacionTurno(numeroTelefono, slotId, cobertura, codigoTurno);

        return ResponseEntity.ok(getCotizacionTurnoAgenteResponse);

    }

    /**
     * Solicita un turno para el paciente del canal en un horario disponible (ECU-TURN-3).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param createTurnoAgenteRequest {@code CreateTurnoAgenteRequest} horario elegido
     * @return {@code ResponseEntity<CreateTurnoAgenteResponse>} el turno creado con status 201
     */
    @PostMapping("/Turno")
    public ResponseEntity<CreateTurnoAgenteResponse> createTurno(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @Valid @RequestBody CreateTurnoAgenteRequest createTurnoAgenteRequest) {

        log.info("Solicitud recibida: solicitar turno desde el canal");

        CreateTurnoAgenteResponse createTurnoAgenteResponse = agenteApp.createTurno(numeroTelefono, createTurnoAgenteRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createTurnoAgenteResponse);

    }

    /**
     * Lista los turnos del paciente del canal que admiten una acción (cancelar, reprogramar, confirmar):
     * estado vigente habilitado y plazo de la acción no vencido.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param accion {@code AccionTurnoAgente} acción que el paciente quiere hacer
     * @return {@code ResponseEntity<List<ListTurnoAgenteResponse>>} los turnos con status 200
     */
    @GetMapping("/Turno")
    public ResponseEntity<List<ListTurnoAgenteResponse>> listTurnos(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @RequestParam AccionTurnoAgente accion) {

        log.info("Solicitud recibida: listar turnos del paciente del canal para {}", accion);

        List<ListTurnoAgenteResponse> listTurnoAgenteResponses = agenteApp.listTurnos(numeroTelefono, accion);

        return ResponseEntity.ok(listTurnoAgenteResponses);

    }

    /**
     * Cancela un turno del paciente del canal (ECU-TURN-4). No borra el registro: lo pasa a CANCELADO.
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param codigo {@code String} código del turno a cancelar
     * @return {@code ResponseEntity<Void>} sin contenido con status 204
     */
    @DeleteMapping("/Turno/{codigo}")
    public ResponseEntity<Void> cancelTurno(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @PathVariable String codigo) {

        log.info("Solicitud recibida: cancelar turno {} desde el canal", codigo);

        agenteApp.cancelTurno(numeroTelefono, codigo);

        return ResponseEntity.noContent().build();

    }

    /**
     * Reprograma un turno del paciente del canal en otro horario (ECU-TURN-5).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param codigo {@code String} código del turno a reprogramar
     * @param reprogramTurnoAgenteRequest {@code ReprogramTurnoAgenteRequest} nuevo horario elegido
     * @return {@code ResponseEntity<ReprogramTurnoAgenteResponse>} el turno nuevo con status 200
     */
    @PatchMapping("/Turno/{codigo}/Reprogramacion")
    public ResponseEntity<ReprogramTurnoAgenteResponse> reprogramTurno(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @PathVariable String codigo,
            @Valid @RequestBody ReprogramTurnoAgenteRequest reprogramTurnoAgenteRequest) {

        log.info("Solicitud recibida: reprogramar turno {} desde el canal", codigo);

        ReprogramTurnoAgenteResponse reprogramTurnoAgenteResponse =
                agenteApp.reprogramTurno(numeroTelefono, codigo, reprogramTurnoAgenteRequest);

        return ResponseEntity.ok(reprogramTurnoAgenteResponse);

    }

    /**
     * Confirma un turno del paciente del canal (ECU-TURN-6).
     *
     * @param numeroTelefono {@code String} teléfono verificado del canal (header {@code X-Paciente-Telefono})
     * @param codigo {@code String} código del turno a confirmar
     * @return {@code ResponseEntity<ConfirmTurnoAgenteResponse>} el turno confirmado con status 200
     */
    @PatchMapping("/Turno/{codigo}/Confirmacion")
    public ResponseEntity<ConfirmTurnoAgenteResponse> confirmTurno(
            @RequestHeader(value = HEADER_TELEFONO, required = false) String numeroTelefono,
            @PathVariable String codigo) {

        log.info("Solicitud recibida: confirmar turno {} desde el canal", codigo);

        ConfirmTurnoAgenteResponse confirmTurnoAgenteResponse = agenteApp.confirmTurno(numeroTelefono, codigo);

        return ResponseEntity.ok(confirmTurnoAgenteResponse);

    }

    //endregion

}
