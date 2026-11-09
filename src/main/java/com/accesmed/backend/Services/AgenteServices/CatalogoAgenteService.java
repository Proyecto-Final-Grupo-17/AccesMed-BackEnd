package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.EstadoPrestacion;
import com.accesmed.backend.Domain.IndicacionPrestacion;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.MedicoPrestacion;
import com.accesmed.backend.Domain.Prestacion;
import com.accesmed.backend.Records.Agente.Response.GetOfertaEspecialidadAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListIndicacionPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.ListPrestacionAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchMedicoAgenteResponse;
import com.accesmed.backend.Records.Agente.Response.SearchPrestacionAgenteResponse;
import com.accesmed.backend.Services.DomainServices.EspecialidadDomainService;
import com.accesmed.backend.Services.DomainServices.HistoricoEstadoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.IndicacionPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoDomainService;
import com.accesmed.backend.Services.DomainServices.MedicoPrestacionDomainService;
import com.accesmed.backend.Services.DomainServices.PrestacionDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Mappers.AgenteMapper;
import com.accesmed.backend.Services.Utils.NormalizadorBusqueda;
import com.accesmed.backend.Services.Utils.ResultadoBusqueda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo del canal chatbot (capa {@code Services/AgenteServices}, {@code Docs/Diseno-Chatbot.md
 * §3.3}): resuelve especialidades, prestaciones y médicos por parecido con lo que escribió el
 * paciente y arma las respuestas con la relación {@code MedicoPrestacion} vigente (no todos los
 * médicos de una especialidad hacen todas sus prestaciones) y las indicaciones vigentes.
 * <p>
 * Solo ofrece lo que se puede reservar: prestaciones {@code PUBLICADA}, médicos activos y
 * asignaciones vigentes hoy. La usan únicamente {@code AgenteApp} y {@code ChatwootApp}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoAgenteService {

    //region ========== Constantes ==========

    private static final int LIMITE_RESULTADOS = 10;

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadDomainService especialidadDomainService;
    private final PrestacionDomainService prestacionDomainService;
    private final MedicoDomainService medicoDomainService;
    private final MedicoPrestacionDomainService medicoPrestacionDomainService;
    private final IndicacionPrestacionDomainService indicacionPrestacionDomainService;
    private final HistoricoEstadoPrestacionDomainService historicoEstadoPrestacionDomainService;
    private final AgenteMapper agenteMapper;

    /**
     * Similitud mínima de trigramas para el fallback de las búsquedas por parecido (Diseno-Chatbot §4.5).
     */
    @Value("${accesmed.agente.busqueda.umbral-similitud:0.5}")
    private double umbralSimilitud;

    //endregion

    //region ========== Métodos ==========

    /**
     * Resuelve una especialidad activa por parecido con el nombre ("cardio" → Cardiología). Si una
     * coincidencia es exacta (sin mayúsculas ni tildes) gana sobre las demás.
     *
     * @param nombre {@code String} nombre aproximado de la especialidad
     * @return {@code Especialidad} la única especialidad que coincide
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no coincide ninguna
     * @throws ReglaNegocioException {@code ReglaNegocioException} si coinciden varias
     */
    public Especialidad findEspecialidadByNombreAproximado(String nombre) {

        List<Especialidad> especialidadesParecidas = especialidadDomainService
                .searchEspecialidadesActivasByNombre(nombre, umbralSimilitud).resultados();

        //Ninguna parecida
        if (especialidadesParecidas.isEmpty()) {
            log.warn("No se encontró una especialidad parecida a: {}", nombre);
            throw new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                    "No encontramos la especialidad \"" + nombre + "\". Pedí el listado de especialidades.");
        }

        //Una sola, o una exacta entre varias
        Especialidad especialidadElegida = findUnicaOExacta(especialidadesParecidas, Especialidad::getNombre, nombre);
        if (especialidadElegida == null) {
            List<String> opciones = especialidadesParecidas.stream().map(Especialidad::getNombre).toList();
            log.warn("Especialidad ambigua: {} coincide con {}", nombre, opciones);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_AMBIGUA",
                    "Hay varias especialidades parecidas a \"" + nombre + "\": " + String.join(", ", opciones)
                            + ". Preguntale al paciente cuál.");
        }

        return especialidadElegida;

    }

    /**
     * Arma la oferta de una especialidad: sus prestaciones publicadas, cada una con los médicos que
     * la atienden hoy. Las prestaciones sin médicos se omiten (no se pueden reservar).
     *
     * @param especialidad {@code Especialidad} especialidad activa
     * @return {@code GetOfertaEspecialidadAgenteResponse} oferta con la relación prestación ↔ médico
     */
    public GetOfertaEspecialidadAgenteResponse buildOfertaEspecialidad(Especialidad especialidad) {

        LocalDate hoy = LocalDate.now();

        List<ListPrestacionAgenteResponse> prestacionesOfrecidas = prestacionDomainService
                .findPrestacionesPublicadasByEspecialidadId(especialidad.getId())
                .stream()
                .map(prestacion -> agenteMapper.toListPrestacionResponse(prestacion,
                        isValidacionRequerida(prestacion.getId()), findMedicosQueAtienden(prestacion.getId(), hoy)))
                .filter(listPrestacionAgenteResponse -> !listPrestacionAgenteResponse.medicos().isEmpty())
                .toList();

        GetOfertaEspecialidadAgenteResponse getOfertaEspecialidadAgenteResponse =
                agenteMapper.toGetOfertaEspecialidadResponse(especialidad.getNombre(), prestacionesOfrecidas);
        return getOfertaEspecialidadAgenteResponse;

    }

    /**
     * Busca prestaciones publicadas por parecido con el nombre, con su especialidad, sus
     * indicaciones vigentes y los médicos que las atienden hoy.
     *
     * @param nombre {@code String} nombre aproximado de la prestación
     * @return {@code List<SearchPrestacionAgenteResponse>} hasta 10 coincidencias (vacía si no hay)
     */
    public List<SearchPrestacionAgenteResponse> searchPrestaciones(String nombre) {

        LocalDate hoy = LocalDate.now();

        ResultadoBusqueda<Prestacion> resultadoBusqueda = prestacionDomainService.searchPrestacionesByNombre(nombre, umbralSimilitud);

        List<SearchPrestacionAgenteResponse> searchPrestacionAgenteResponses = filterPublicadas(resultadoBusqueda.resultados())
                .stream()
                .limit(LIMITE_RESULTADOS)
                .map(prestacion -> {
                    List<IndicacionPrestacion> indicacionesVigentes =
                            indicacionPrestacionDomainService.findIndicacionesPrestacionVigentesByPrestacionId(prestacion.getId());
                    return agenteMapper.toSearchPrestacionResponse(prestacion,
                            indicacionesVigentes.stream().anyMatch(IndicacionPrestacion::getRequiereValidacion),
                            agenteMapper.toListIndicacionPrestacionResponses(indicacionesVigentes),
                            findMedicosQueAtienden(prestacion.getId(), hoy), resultadoBusqueda.coincidencia());
                })
                .toList();
        return searchPrestacionAgenteResponses;

    }

    /**
     * Busca médicos activos por parecido con su nombre o apellido, con su especialidad y las
     * prestaciones publicadas que atienden hoy.
     *
     * @param nombre {@code String} nombre o apellido aproximado del médico
     * @return {@code List<SearchMedicoAgenteResponse>} hasta 10 coincidencias (vacía si no hay)
     */
    public List<SearchMedicoAgenteResponse> searchMedicos(String nombre) {

        LocalDate hoy = LocalDate.now();

        ResultadoBusqueda<Medico> resultadoBusqueda = medicoDomainService.searchMedicosActivosByNombre(nombre, umbralSimilitud);

        List<SearchMedicoAgenteResponse> searchMedicoAgenteResponses = resultadoBusqueda.resultados()
                .stream()
                .limit(LIMITE_RESULTADOS)
                .map(medico -> agenteMapper.toSearchMedicoResponse(medico,
                        findPrestacionesQueAtiende(medico.getId(), hoy).stream().map(Prestacion::getNombre).toList(),
                        resultadoBusqueda.coincidencia()))
                .toList();
        return searchMedicoAgenteResponses;

    }

    /**
     * Resuelve, por parecido con el nombre, una de las prestaciones publicadas que el médico atiende
     * hoy (para consultar sus horarios).
     *
     * @param nombrePrestacion {@code String} nombre aproximado de la prestación
     * @param medico {@code Medico} médico activo
     * @return {@code Prestacion} la única prestación del médico que coincide
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ninguna prestación publicada se parece al nombre
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el médico no atiende ninguna
     *         prestación parecida, o si atiende varias
     */
    public Prestacion findPrestacionDeMedicoByNombreAproximado(String nombrePrestacion, Medico medico) {

        //Prestaciones publicadas parecidas al nombre (en toda la clínica)
        List<Prestacion> prestacionesParecidas = filterPublicadas(
                prestacionDomainService.searchPrestacionesByNombre(nombrePrestacion, umbralSimilitud).resultados());
        if (prestacionesParecidas.isEmpty()) {
            log.warn("No se encontró una prestación publicada parecida a: {}", nombrePrestacion);
            throw new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                    "No encontramos la prestación \"" + nombrePrestacion + "\".");
        }

        //Quedarse con las que el médico atiende hoy
        Set<UUID> prestacionesDelMedico = findPrestacionesQueAtiende(medico.getId(), LocalDate.now()).stream()
                .map(Prestacion::getId)
                .collect(Collectors.toSet());
        List<Prestacion> prestacionesDelMedicoParecidas = prestacionesParecidas.stream()
                .filter(prestacion -> prestacionesDelMedico.contains(prestacion.getId()))
                .toList();
        if (prestacionesDelMedicoParecidas.isEmpty()) {
            log.warn("El médico {} no atiende una prestación parecida a {}", medico.getId(), nombrePrestacion);
            throw new ReglaNegocioException(getClass(), "MEDICO_NO_ATIENDE_PRESTACION",
                    "El médico elegido no atiende esa prestación.");
        }

        //Una sola, o una exacta entre varias
        Prestacion prestacionElegida = findUnicaOExacta(prestacionesDelMedicoParecidas, Prestacion::getNombre, nombrePrestacion);
        if (prestacionElegida == null) {
            List<String> opciones = prestacionesDelMedicoParecidas.stream().map(Prestacion::getNombre).toList();
            log.warn("Prestación ambigua para el médico {}: {} coincide con {}", medico.getId(), nombrePrestacion, opciones);
            throw new ReglaNegocioException(getClass(), "PRESTACION_AMBIGUA",
                    "Hay varias prestaciones parecidas a \"" + nombrePrestacion + "\": " + String.join(", ", opciones)
                            + ". Preguntale al paciente cuál.");
        }

        return prestacionElegida;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Lista los médicos activos que atienden una prestación en la fecha dada.
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @param fecha {@code LocalDate} fecha de vigencia de la asignación
     * @return {@code List<ListMedicoAgenteResponse>} médicos ordenados por apellido y nombre
     */
    private List<ListMedicoAgenteResponse> findMedicosQueAtienden(UUID prestacionId, LocalDate fecha) {

        List<Medico> medicosQueAtienden = medicoPrestacionDomainService.findAsignacionesVigentesByPrestacion(prestacionId, fecha)
                .stream()
                .map(MedicoPrestacion::getMedico)
                .filter(medico -> medico.getDeletedAt() == null)
                .distinct()
                .sorted(Comparator.comparing(Medico::getApellido).thenComparing(Medico::getNombre))
                .toList();

        List<ListMedicoAgenteResponse> listMedicoAgenteResponses = agenteMapper.toListMedicoResponses(medicosQueAtienden);
        return listMedicoAgenteResponses;

    }

    /**
     * Lista las prestaciones publicadas que un médico atiende en la fecha dada.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @param fecha {@code LocalDate} fecha de vigencia de la asignación
     * @return {@code List<Prestacion>} prestaciones publicadas ordenadas por nombre
     */
    private List<Prestacion> findPrestacionesQueAtiende(UUID medicoId, LocalDate fecha) {

        List<Prestacion> prestacionesAsignadas = medicoPrestacionDomainService.findAsignacionesVigentesByMedico(medicoId, fecha)
                .stream()
                .map(MedicoPrestacion::getPrestacion)
                .distinct()
                .toList();

        List<Prestacion> prestacionesQueAtiende = filterPublicadas(prestacionesAsignadas).stream()
                .sorted(Comparator.comparing(Prestacion::getNombre))
                .toList();
        return prestacionesQueAtiende;

    }

    /**
     * Deja solo las prestaciones cuyo estado vigente es {@code PUBLICADA}, manteniendo el orden.
     *
     * @param prestaciones {@code List<Prestacion>} prestaciones en cualquier estado
     * @return {@code List<Prestacion>} las publicadas
     */
    private List<Prestacion> filterPublicadas(List<Prestacion> prestaciones) {

        if (prestaciones.isEmpty()) {
            return List.of();
        }

        Map<UUID, EstadoPrestacion> estadosVigentes = historicoEstadoPrestacionDomainService.getEstadosVigentes(
                prestaciones.stream().map(Prestacion::getId).toList());

        List<Prestacion> prestacionesPublicadas = prestaciones.stream()
                .filter(prestacion -> estadosVigentes.get(prestacion.getId()) == EstadoPrestacion.PUBLICADA)
                .toList();
        return prestacionesPublicadas;

    }

    /**
     * Indica si alguna indicación vigente de la prestación requiere validación (el turno arranca en
     * {@code ESPERA_VALIDACION}, mismo criterio que la reserva).
     *
     * @param prestacionId {@code UUID} identificador de la prestación
     * @return {@code boolean} {@code true} si alguna requiere validación
     */
    private boolean isValidacionRequerida(UUID prestacionId) {

        boolean validacionRequerida = indicacionPrestacionDomainService
                .findIndicacionesPrestacionVigentesByPrestacionId(prestacionId)
                .stream()
                .anyMatch(IndicacionPrestacion::getRequiereValidacion);
        return validacionRequerida;

    }

    /**
     * Elige entre candidatos parecidos: el único si hay uno solo, o el que coincide exactamente
     * (sin mayúsculas ni tildes) con lo que escribió el paciente.
     *
     * @param candidatos {@code List<T>} candidatos parecidos (no vacía)
     * @param nombreDe {@code Function<T, String>} cómo obtener el nombre de un candidato
     * @param nombreBuscado {@code String} lo que escribió el paciente
     * @param <T> tipo del candidato
     * @return {@code T} el candidato elegido, o {@code null} si la elección es ambigua
     */
    private <T> T findUnicaOExacta(List<T> candidatos, Function<T, String> nombreDe, String nombreBuscado) {

        if (candidatos.size() == 1) {
            return candidatos.getFirst();
        }

        String nombreBuscadoNormalizado = NormalizadorBusqueda.normalizarTexto(nombreBuscado);
        List<T> exactos = candidatos.stream()
                .filter(candidato -> NormalizadorBusqueda.normalizarTexto(nombreDe.apply(candidato)).equals(nombreBuscadoNormalizado))
                .toList();

        T candidatoElegido = exactos.size() == 1 ? exactos.getFirst() : null;
        return candidatoElegido;

    }

    //endregion

}
