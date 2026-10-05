package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.AgendaHorariosDia;
import com.accesmed.backend.Domain.AgendaHorariosDia_;
import com.accesmed.backend.Domain.AgendaMedico_;
import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Prestacion_;
import com.accesmed.backend.Records.AgendaMedico.Criteria.AgendaHorariosCriteria;
import com.accesmed.backend.Records.AgendaMedico.Response.ListAgendaHorarioResponse;
import com.accesmed.backend.Records.AgendaMedico.Response.ListHorarioDisponibleResponse;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Repositories.AgendaHorariosDiaRepository;
import com.accesmed.backend.Repositories.ClinicaRepository;
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

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code AgendaHorariosDia}, incluido el filtrado
 * dinámico por {@link AgendaHorariosCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado
 * dinámico}). Lo comparten {@code listHorariosAgenda} (panel) y {@code listHorariosDisponibles}
 * (chatbot): {@code createSpecification} aplica la única guarda común a ambos
 * ({@code deletedAt} vacío); {@link #findHorariosDisponibles} agrega las guardas propias
 * del listado del chatbot, que no son filtros que el front pueda pedir o no pedir, sino
 * reglas fijas del endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaHorariosDiaQueryService extends AbstractFiltroQueryService<AgendaHorariosDia, AgendaHorariosCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final AgendaHorariosDiaRepository agendaHorariosDiaRepository;
    private final AgendaMedicoMapper agendaMedicoMapper;
    private final ClinicaRepository clinicaRepository;
    private final AlcanceMedicoService alcanceMedicoService;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<AgendaHorariosDia> getRepository() {

        return agendaHorariosDiaRepository;

    }

    /**
     * Traduce un {@link AgendaHorariosCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor. Guarda fija
     * común a ambos listados: {@code deletedAt} vacío.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<AgendaHorariosDia>} especificación equivalente al criteria
     */
    @Override
    protected Specification<AgendaHorariosDia> createSpecification(AgendaHorariosCriteria criteria) {

        log.debug("Armando specification de horarios de agenda: criteria={}", criteria);

        Specification<AgendaHorariosDia> specification = Specification
                .<AgendaHorariosDia>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(AgendaHorariosDia_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), AgendaHorariosDia_.id));
        }
        if (criteria.getPrestacionId() != null) {
            specification = specification.and(buildSpecification(criteria.getPrestacionId(),
                    root -> root.join(AgendaHorariosDia_.prestacion, JoinType.LEFT).get(Prestacion_.id)));
        }
        if (criteria.getAgendaMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getAgendaMedicoId(),
                    root -> root.join(AgendaHorariosDia_.agendaMedico, JoinType.LEFT).get(AgendaMedico_.id)));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    root -> root.join(AgendaHorariosDia_.agendaMedico, JoinType.LEFT)
                            .join(AgendaMedico_.medico, JoinType.LEFT).get(Medico_.id)));
        }
        if (criteria.getFecha() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getFecha(), AgendaHorariosDia_.fecha));
        }
        if (criteria.getHoraDesde() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getHoraDesde(), AgendaHorariosDia_.horaDesde));
        }
        if (criteria.getEstaOcupada() != null) {
            specification = specification.and(buildSpecification(criteria.getEstaOcupada(), AgendaHorariosDia_.estaOcupada));
        }
        if (criteria.getCreatedBy() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCreatedBy(), Auditable_.createdBy));
        }

        return specification;

    }

    /**
     * Busca horarios de agenda del panel según el criteria, devolviendo una página mapeada a DTOs.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListAgendaHorarioResponse>} página de horarios mapeados
     */
    public PageResponse<ListAgendaHorarioResponse> findHorariosByCriteria(AgendaHorariosCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando horarios de agenda por criteria: {}, pageable: {}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        // Aplicar scope: si el criterio trae medicoId, reemplazarlo con el del usuario si es médico
        AgendaHorariosCriteria criteriaEfectivo = criteria != null ? criteria : new AgendaHorariosCriteria();
        UUID medicoIdEfectivo = alcanceMedicoService.resolveMedicoId(usuarioDetails, criteriaEfectivo.getMedicoId() != null ? criteriaEfectivo.getMedicoId().getEquals() : null);
        if (medicoIdEfectivo != null) {
            criteriaEfectivo.setMedicoId(new com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter());
            criteriaEfectivo.getMedicoId().setEquals(medicoIdEfectivo);
        }

        Page<AgendaHorariosDia> horariosPaginados = findByCriteria(criteriaEfectivo, pageable);
        return PageResponse.from(horariosPaginados, horario -> agendaMedicoMapper.toListHorarioResponse(horario,
                tieneAuditoria ? agendaMedicoMapper.toAuditoria(horario) : null));

    }

    /**
     * Busca horarios disponibles para el chatbot: sobre el criteria del cliente, aplica
     * además las guardas fijas propias de este listado ({@code estaOcupada = false},
     * {@code ahora < fechaLimiteReserva}, {@code fecha <= hoy + diasMaximosAnticipacionReserva}).
     * No son filtros opcionales: el front no puede pedir un slot ocupado ni uno fuera del
     * horizonte de reserva de la clínica. El horizonte se obtiene de la configuración de la clínica.
     *
     * @param criteria {@code AgendaHorariosCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListHorarioDisponibleResponse>} página de horarios disponibles mapeados
     */
    public PageResponse<ListHorarioDisponibleResponse> findHorariosDisponibles(AgendaHorariosCriteria criteria, Pageable pageable) {

        log.debug("Buscando horarios disponibles por criteria: {}, pageable: {}", criteria, pageable);

        //Resolver el horizonte de reserva configurado en la única fila de Clinica (garantizada por el esquema)
        int diasMaximosAnticipacionReserva = clinicaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No se encontró la fila de configuración de la clínica");
                    return new RecursoNoEncontradoException(getClass(), "CLINICA_NO_ENCONTRADA",
                            "Falta la configuración de la clínica. Contactá a soporte.");
                })
                .getDiasMaximosAnticipacionReserva();
        ZonedDateTime ahora = ZonedDateTime.now();
        LocalDate fechaLimiteHorizonte = LocalDate.now().plusDays(diasMaximosAnticipacionReserva);

        Specification<AgendaHorariosDia> specification = createSpecification(criteria)
                .and((root, query, cb) -> cb.isFalse(root.get(AgendaHorariosDia_.estaOcupada)))
                .and((root, query, cb) -> cb.greaterThan(root.get(AgendaHorariosDia_.fechaLimiteReserva), ahora))
                .and((root, query, cb) -> cb.lessThanOrEqualTo(root.get(AgendaHorariosDia_.fecha), fechaLimiteHorizonte));

        Page<AgendaHorariosDia> horariosPaginados = agendaHorariosDiaRepository.findAll(specification, pageable);
        return PageResponse.from(horariosPaginados, agendaMedicoMapper::toListHorarioDisponibleResponse);

    }

    //endregion

}
