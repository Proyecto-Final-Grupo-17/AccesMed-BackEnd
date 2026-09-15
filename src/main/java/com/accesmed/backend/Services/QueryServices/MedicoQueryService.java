package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.AgendaMedico;
import com.accesmed.backend.Domain.AgendaMedico_;
import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.Medico.Criteria.MedicoCriteria;
import com.accesmed.backend.Records.Medico.Response.GetMedicoResponse;
import com.accesmed.backend.Records.Medico.Response.GetPrestacionAnidadaResponse;
import com.accesmed.backend.Records.Medico.Response.ListMedicoResponse;
import com.accesmed.backend.Repositories.MedicoPrestacionRepository;
import com.accesmed.backend.Repositories.MedicoRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.MedicoMapper;
import com.accesmed.backend.Services.Mappers.MedicoPrestacionMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.BooleanFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Medico}, incluido el filtrado dinámico por
 * {@link MedicoCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicoQueryService extends AbstractFiltroQueryService<Medico, MedicoCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final MedicoRepository medicoRepository;
    private final MedicoMapper medicoMapper;
    private final MedicoPrestacionRepository medicoPrestacionRepository;
    private final MedicoPrestacionMapper medicoPrestacionMapper;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Medico> getRepository() {

        return medicoRepository;

    }

    /**
     * Busca el médico activo que cumple el criteria proporcionado, mapeando al DTO
     * {@code GetMedicoResponse} que incluye sus {@code prestaciones} asociadas vía
     * {@code MedicoPrestacion}.
     *
     * @param criteria {@code MedicoCriteria} filtros a aplicar (típicamente por {@code id})
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetMedicoResponse} el médico activo con sus prestaciones
     * @throws RecursoNoEncontradoException si ningún médico activo cumple el criteria
     */
    public GetMedicoResponse findMedicoByCriteria(MedicoCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando médico por criteria: {}", criteria);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);

        Medico medicoActivo = getMedicoActivo(criteria);
        List<GetPrestacionAnidadaResponse> prestacionesResponse = medicoPrestacionMapper
                .toGetPrestacionAnidadaResponses(medicoPrestacionRepository.findByMedico_IdAndVigenteAt(medicoActivo.getId(), ZonedDateTime.now()));
        return medicoMapper.toGetResponse(medicoActivo, prestacionesResponse,
                tieneAuditoria ? medicoMapper.toAuditoria(medicoActivo) : null);

    }

    /**
     * Busca médicos activos según el criteria, devolviendo una página mapeada a DTOs.
     *
     * @param criteria {@code MedicoCriteria} filtros a aplicar
     * @param pageable {@code Pageable} paginación (ordenamiento y límite)
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListMedicoResponse>} página de DTOs mapeados
     */
    public PageResponse<ListMedicoResponse> findMedicos(MedicoCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando médicos por criteria: {}, pageable: {}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        Page<Medico> medicosPaginados = findByCriteria(criteria, pageable);
        return PageResponse.from(medicosPaginados,
                medico -> medicoMapper.toListResponse(medico,
                        tieneAuditoria ? medicoMapper.toAuditoria(medico) : null));

    }

    /**
     * Busca el médico activo que cumple el criteria proporcionado (método interno).
     * A diferencia de {@link #findByCriteria}, devuelve un único médico en vez de una página.
     *
     * @param criteria {@code MedicoCriteria} filtros a aplicar
     * @return {@code Medico} el médico activo que cumple el criteria
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         médico activo cumple el criteria
     */
    private Medico getMedicoActivo(MedicoCriteria criteria) {

        log.debug("Buscando médico activo por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún médico activo que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_NO_ENCONTRADO",
                            "No existe un médico activo que cumpla el criteria proporcionado.");
                });

    }

    /**
     * Traduce un {@link MedicoCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code MedicoCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Medico>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Medico> createSpecification(MedicoCriteria criteria) {

        log.debug("Armando specification de médicos: criteria={}", criteria);

        Specification<Medico> specification = Specification
                .<Medico>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(Medico_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Medico_.id));
        }
        if (criteria.getMatricula() != null) {
            specification = specification.and(buildStringSpecification(criteria.getMatricula(), Medico_.matricula));
        }
        if (criteria.getDni() != null) {
            specification = specification.and(buildStringSpecification(criteria.getDni(), Medico_.dni));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Medico_.nombre));
        }
        if (criteria.getApellido() != null) {
            specification = specification.and(buildStringSpecification(criteria.getApellido(), Medico_.apellido));
        }
        if (criteria.getEmail() != null) {
            specification = specification.and(buildStringSpecification(criteria.getEmail(), Medico_.email));
        }
        if (criteria.getEspecialidadId() != null) {
            specification = specification.and(buildSpecification(criteria.getEspecialidadId(),
                    root -> root.join(Medico_.especialidad, JoinType.LEFT).get(Especialidad_.id)));
        }
        if (criteria.getCreatedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getCreatedDate(), Auditable_.createdDate));
        }
        if (criteria.getLastModifiedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getLastModifiedDate(), Auditable_.lastModifiedDate));
        }
        if (criteria.getCreatedBy() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCreatedBy(), Auditable_.createdBy));
        }
        if (criteria.getTieneAgendaVigente() != null) {
            ZonedDateTime fechaReferencia = criteria.getAgendaVigenteAl() != null ? criteria.getAgendaVigenteAl() : ZonedDateTime.now();
            specification = specification.and(buildTieneAgendaVigenteSpecification(criteria.getTieneAgendaVigente(), fechaReferencia));
        }

        return specification;

    }

    /**
     * Arma el fragmento de {@link Specification} que filtra médicos según si tienen (o no)
     * una {@code AgendaMedico} vigente en una fecha de referencia, con una subconsulta
     * correlacionada {@code EXISTS}/{@code NOT EXISTS} (precedente:
     * {@code PrestacionQueryService.buildEstadoVigenteSpecification}). Resuelve la
     * consulta "médicos activos sin agenda vigente" de §5 AGEN sin necesidad de un
     * endpoint propio.
     *
     * @param filter {@code BooleanFilter} si se pide {@code true} (tiene agenda vigente) o
     *        {@code false} (no tiene)
     * @param fechaReferencia {@code ZonedDateTime} fecha contra la cual evaluar la vigencia de la agenda
     * @return {@code Specification<Medico>} fragmento que filtra por tenencia de agenda vigente
     */
    private Specification<Medico> buildTieneAgendaVigenteSpecification(BooleanFilter filter, ZonedDateTime fechaReferencia) {

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<AgendaMedico> agenda = subquery.from(AgendaMedico.class);
            subquery.select(agenda.get(AgendaMedico_.id));

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(agenda.get(AgendaMedico_.medico), root));
            predicates.add(cb.lessThanOrEqualTo(agenda.get(AgendaMedico_.fechaHoraInicioVigencia), fechaReferencia));
            predicates.add(cb.greaterThan(agenda.get(AgendaMedico_.fechaHoraFinVigencia), fechaReferencia));

            subquery.where(predicates.toArray(new Predicate[0]));

            boolean debeTenerAgendaVigente = filter.getEquals() == null || filter.getEquals();
            return debeTenerAgendaVigente ? cb.exists(subquery) : cb.not(cb.exists(subquery));
        };

    }

    //endregion

}
