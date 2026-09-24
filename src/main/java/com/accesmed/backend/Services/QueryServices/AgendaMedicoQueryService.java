package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.AgendaMedico_;
import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaMedicoCriteria;
import com.accesmed.backend.Records.AgendaMedico.Response.DiaAgendaResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.GetAgendaMedicoResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaMedicoResponse;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Repositories.AgendaHorariosDiaRepository;
import com.accesmed.backend.Repositories.AgendaMedicoRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AlcanceMedicoService;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.AgendaMedicoMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultas de lectura para la entidad {@code AgendaMedico}, incluido el filtrado dinámico
 * por {@link AgendaMedicoCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * Sin guarda fija de vigencia: {@code createSpecification} no agrega ningún filtro
 * implícito, igual que {@code PrestacionQueryService} — el selector del front tiene que
 * poder listar también los períodos vencidos y los programados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaMedicoQueryService extends AbstractFiltroQueryService<AgendaMedico, AgendaMedicoCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaMedicoRepository agendaMedicoRepository;
    private final AgendaHorariosDiaRepository agendaHorariosDiaRepository;
    private final AgendaMedicoMapper agendaMedicoMapper;
    private final AlcanceMedicoService alcanceMedicoService;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<AgendaMedico> getRepository() {

        return agendaMedicoRepository;

    }

    /**
     * Traduce un {@link AgendaMedicoCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code AgendaMedicoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<AgendaMedico>} especificación equivalente al criteria
     */
    @Override
    protected Specification<AgendaMedico> createSpecification(AgendaMedicoCriteria criteria) {

        log.debug("Armando specification de agendas médicas: criteria={}", criteria);

        Specification<AgendaMedico> specification = Specification.unrestricted();

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), AgendaMedico_.id));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    root -> root.join(AgendaMedico_.medico, JoinType.LEFT).get(Medico_.id)));
        }
        if (criteria.getEspecialidadId() != null) {
            specification = specification.and(buildSpecification(criteria.getEspecialidadId(),
                    root -> root.join(AgendaMedico_.medico, JoinType.LEFT).join(Medico_.especialidad, JoinType.LEFT).get(Especialidad_.id)));
        }
        if (criteria.getFechaInicioVigencia() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFechaInicioVigencia(), AgendaMedico_.fechaInicioVigencia));
        }
        if (criteria.getFechaFinVigencia() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFechaFinVigencia(), AgendaMedico_.fechaFinVigencia));
        }
        if (criteria.getVigenteAl() != null) {
            specification = specification.and((root, query, cb) -> cb.and(
                    cb.lessThanOrEqualTo(root.get(AgendaMedico_.fechaInicioVigencia), criteria.getVigenteAl()),
                    cb.greaterThanOrEqualTo(root.get(AgendaMedico_.fechaFinVigencia), criteria.getVigenteAl())));
        }
        if (criteria.getCreatedBy() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCreatedBy(), Auditable_.createdBy));
        }

        return specification;

    }

    /**
     * Busca la agenda médica activa que cumple el criteria proporcionado (típicamente
     * una agenda puntual identificada por su {@code id}), con sus días y horarios activos.
     *
     * @param criteria {@code AgendaMedicoCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetAgendaMedicoResponse} la agenda encontrada con sus días y horarios
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException}
     *         si ninguna agenda cumple el criteria
     */
    public GetAgendaMedicoResponse findAgendaMedicoByCriteria(AgendaMedicoCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando agenda médica por criteria: {}", criteria);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        // Aplicar scope: si el criterio trae medicoId, reemplazarlo con el del usuario si es médico
        AgendaMedicoCriteria criteriaEfectivo = criteria != null ? criteria : new AgendaMedicoCriteria();
        UUID medicoIdEfectivo = alcanceMedicoService.resolveMedicoId(usuarioDetails, criteriaEfectivo.getMedicoId() != null ? criteriaEfectivo.getMedicoId().getEquals() : null);
        if (medicoIdEfectivo != null) {
            criteriaEfectivo.setMedicoId(new com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter());
            criteriaEfectivo.getMedicoId().setEquals(medicoIdEfectivo);
        }

        AgendaMedico agendaMedicoEncontrada = findOneByCriteria(criteriaEfectivo)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna agenda médica que cumpla el criteria: {}", criteriaEfectivo);
                    return new RecursoNoEncontradoException(getClass(),
                            "AGENDA_MEDICO_NO_ENCONTRADA",
                            "No se encontró ninguna agenda que coincida con la búsqueda.");
                });

        //Obtener los horarios activos de la agenda
        List<AgendaHorariosDia> horariosActivos = agendaHorariosDiaRepository.findByAgendaMedico_IdAndDeletedAtIsNull(agendaMedicoEncontrada.getId());
        List<DiaAgendaResponse> diasResponse = agendaMedicoMapper.toDiaAgendaResponses(horariosActivos);

        return agendaMedicoMapper.toGetResponse(agendaMedicoEncontrada, diasResponse,
                tieneAuditoria ? agendaMedicoMapper.toAuditoria(agendaMedicoEncontrada) : null);

    }

    /**
     * Busca agendas médicas según el criteria, devolviendo una página mapeada a DTOs
     * incluyendo el conteo de días y horarios activos de cada agenda.
     *
     * @param criteria {@code AgendaMedicoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} paginación (ordenamiento y límite)
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListAgendaMedicoResponse>} página de DTOs mapeados con conteos
     */
    public PageResponse<ListAgendaMedicoResponse> findAgendasMedicas(AgendaMedicoCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando agendas médicas por criteria: {}, pageable: {}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        // Aplicar scope: si el criterio trae medicoId, reemplazarlo con el del usuario si es médico
        AgendaMedicoCriteria criteriaEfectivo = criteria != null ? criteria : new AgendaMedicoCriteria();
        UUID medicoIdEfectivo = alcanceMedicoService.resolveMedicoId(usuarioDetails, criteriaEfectivo.getMedicoId() != null ? criteriaEfectivo.getMedicoId().getEquals() : null);
        if (medicoIdEfectivo != null) {
            criteriaEfectivo.setMedicoId(new com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter());
            criteriaEfectivo.getMedicoId().setEquals(medicoIdEfectivo);
        }

        Page<AgendaMedico> agendasPaginadas = findByCriteria(criteriaEfectivo, pageable);

        //Obtener conteos de días y horarios activos por cada agenda, en una sola consulta agrupada
        List<UUID> agendaMedicoIds = agendasPaginadas.getContent()
                .stream()
                .map(AgendaMedico::getId)
                .toList();
        Map<UUID, AgendaHorariosDiaRepository.ConteoAgendaMedico> conteosPorAgenda =
                agendaHorariosDiaRepository.countDiasYHorariosActivosByAgendaMedicoIds(agendaMedicoIds)
                        .stream()
                        .collect(Collectors.toMap(
                                AgendaHorariosDiaRepository.ConteoAgendaMedico::getAgendaMedicoId,
                                Function.identity()
                        ));

        return PageResponse.from(agendasPaginadas, agenda -> {
            AgendaHorariosDiaRepository.ConteoAgendaMedico conteo = conteosPorAgenda.get(agenda.getId());
            long cantidadDias = conteo != null ? conteo.getCantidadDias() : 0L;
            long cantidadHorarios = conteo != null ? conteo.getCantidadHorarios() : 0L;
            return agendaMedicoMapper.toListResponse(agenda, cantidadDias, cantidadHorarios,
                    tieneAuditoria ? agendaMedicoMapper.toAuditoria(agenda) : null);
        });

    }

    //endregion

}
