package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Domain.HistoricoEstadoPlan;
import com.accesmed.backend.Domain.ObraSocial;
import com.accesmed.backend.Domain.ObraSocial_;
import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Domain.Plan;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;
import com.accesmed.backend.Records.ObraSocial.Criteria.ObraSocialCriteria;
import com.accesmed.backend.Records.ObraSocial.Response.GetObraSocialResponse;
import com.accesmed.backend.Records.ObraSocial.Response.GetPlanAnidadoResponse;
import com.accesmed.backend.Records.ObraSocial.Response.ListObraSocialResponse;
import com.accesmed.backend.Records.Plan.Response.GetCoberturaAnidadaResponse;
import com.accesmed.backend.Repositories.HistoricoEstadoPlanRepository;
import com.accesmed.backend.Repositories.ObraSocialPlanPrestacionRepository;
import com.accesmed.backend.Repositories.ObraSocialRepository;
import com.accesmed.backend.Repositories.PlanRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AutorizacionService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.ObraSocialMapper;
import com.accesmed.backend.Services.Mappers.ObraSocialPlanPrestacionMapper;
import com.accesmed.backend.Services.Mappers.PlanMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
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
import java.util.stream.Collectors;

/**
 * Consultas de lectura para la entidad {@code ObraSocial}, incluido el filtrado dinámico
 * por {@link ObraSocialCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ObraSocialQueryService extends AbstractFiltroQueryService<ObraSocial, ObraSocialCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final ObraSocialRepository obraSocialRepository;
    private final ObraSocialMapper obraSocialMapper;
    private final PlanRepository planRepository;
    private final PlanMapper planMapper;
    private final HistoricoEstadoPlanRepository historicoEstadoPlanRepository;
    private final ObraSocialPlanPrestacionRepository obraSocialPlanPrestacionRepository;
    private final ObraSocialPlanPrestacionMapper obraSocialPlanPrestacionMapper;
    private final AutorizacionService autorizacionService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<ObraSocial> getRepository() {

        return obraSocialRepository;

    }

    /**
     * Busca la obra social activa que cumple el criteria proporcionado, mapeando al DTO Response
     * incluyendo sus planes asociados.
     *
     * @param criteria {@code ObraSocialCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetObraSocialResponse} la obra social encontrada con sus planes
     * @throws RecursoNoEncontradoException si ninguna obra social activa cumple el criteria
     */
    public GetObraSocialResponse findObraSocialByCriteria(ObraSocialCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando obra social por criteria: {}", criteria);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);

        ObraSocial obraSocialEncontrada = getObraSocialActiva(criteria);
        List<GetPlanAnidadoResponse> planesResponse = mapPlanesAnidados(
                planRepository.findAllByObraSocialId(obraSocialEncontrada.getId()));
        return obraSocialMapper.toGetResponse(obraSocialEncontrada, planesResponse,
                tieneAuditoria ? obraSocialMapper.toAuditoria(obraSocialEncontrada) : null);

    }

    /**
     * Busca obras sociales activas según el criteria, devolviendo una página mapeada a DTOs.
     *
     * @param criteria {@code ObraSocialCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} paginación (ordenamiento y límite)
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListObraSocialResponse>} página de DTOs mapeados
     */
    public PageResponse<ListObraSocialResponse> findObrasSociales(ObraSocialCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando obras sociales por criteria: {}, pageable: {}", criteria, pageable);

        boolean tieneAuditoria = autorizacionService.hasAuthority(usuarioDetails, Permiso.AUDITORIA_CONSULTAR);
        if (!tieneAuditoria && criteria != null) {
            criteria.setCreatedBy(null);
        }

        Page<ObraSocial> obrasSocialesPaginadas = findByCriteria(criteria, pageable);
        return PageResponse.from(obrasSocialesPaginadas,
                obraSocial -> obraSocialMapper.toListResponse(obraSocial,
                        tieneAuditoria ? obraSocialMapper.toAuditoria(obraSocial) : null));

    }

    /**
     * Busca la obra social activa que cumple el criteria proporcionado (método interno).
     *
     * @param criteria {@code ObraSocialCriteria} filtros a aplicar
     * @return {@code ObraSocial} la obra social activa que cumple el criteria
     * @throws RecursoNoEncontradoException si ninguna obra social activa cumple el criteria
     */
    private ObraSocial getObraSocialActiva(ObraSocialCriteria criteria) {

        log.debug("Buscando obra social activa por criteria: {}", criteria);

        return findOneByCriteria(criteria)
                .orElseThrow(() -> {
                    log.warn("No se encontró ninguna obra social activa que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "OBRA_SOCIAL_NO_ENCONTRADA",
                            "No se encontró ninguna obra social que coincida con la búsqueda.");
                });

    }

    /**
     * Traduce un {@link ObraSocialCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code ObraSocialCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<ObraSocial>} especificación equivalente al criteria
     */
    @Override
    protected Specification<ObraSocial> createSpecification(ObraSocialCriteria criteria) {

        log.debug("Armando specification de obras sociales: criteria={}", criteria);

        Specification<ObraSocial> specification = Specification
                .<ObraSocial>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(ObraSocial_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), ObraSocial_.id));
        }
        if (criteria.getCodigo() != null) {
            specification = specification.and(buildStringSpecification(criteria.getCodigo(), ObraSocial_.codigo));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), ObraSocial_.nombre));
        }
        if (criteria.getRazonSocial() != null) {
            specification = specification.and(buildStringSpecification(criteria.getRazonSocial(), ObraSocial_.razonSocial));
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

        return specification;

    }

    /**
     * Mapea una lista de planes a sus responses anidados, resolviendo el estado vigente de
     * todos en una única consulta (evita N+1) y alimentándolo a cada response.
     *
     * @param planes {@code List<Plan>} planes a mapear
     * @return {@code List<GetPlanAnidadoResponse>} responses anidados con su estado vigente
     */
    private List<GetPlanAnidadoResponse> mapPlanesAnidados(List<Plan> planes) {

        List<UUID> planIds = planes.stream().map(Plan::getId).toList();
        Map<UUID, EstadoPlan> estadosVigentes = planIds.isEmpty()
                ? Map.of()
                : historicoEstadoPlanRepository.findByPlanIdInAndFechaHoraFinIsNull(planIds).stream()
                        .collect(Collectors.toMap(historico -> historico.getPlan().getId(), HistoricoEstadoPlan::getEstado));

        Map<UUID, List<GetCoberturaAnidadaResponse>> coberturasPorPlan = planIds.isEmpty()
                ? Map.of()
                : obraSocialPlanPrestacionRepository.findByPlan_IdInAndDeletedAtIsNull(planIds).stream()
                        .collect(Collectors.groupingBy(
                                cobertura -> cobertura.getPlan().getId(),
                                Collectors.mapping(obraSocialPlanPrestacionMapper::toGetCoberturaAnidadaResponse, Collectors.toList())));

        return planes.stream()
                .map(plan -> planMapper.toGetPlanAnidadoResponse(plan, estadosVigentes.get(plan.getId()),
                        coberturasPorPlan.getOrDefault(plan.getId(), List.of())))
                .toList();

    }

    //endregion

}
