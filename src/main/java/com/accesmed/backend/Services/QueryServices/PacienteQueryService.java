package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Auditable_;
import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Domain.Paciente_;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Records.Paciente.Criteria.PacienteCriteria;
import com.accesmed.backend.Records.Paciente.Response.GetObraSocialAnidadaResponse;
import com.accesmed.backend.Records.Paciente.Response.GetPacienteResponse;
import com.accesmed.backend.Records.Paciente.Response.ListPacienteResponse;
import com.accesmed.backend.Repositories.ObraSocialPacienteRepository;
import com.accesmed.backend.Repositories.PacienteRepository;
import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Security.Services.Utils.AlcanceMedicoService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.ObraSocialPacienteMapper;
import com.accesmed.backend.Services.Mappers.PacienteMapper;
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
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Paciente}, incluido el filtrado dinámico
 * por {@link PacienteCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}).
 * {@code createSpecification} excluye siempre las bajas lógicas ({@code deletedAt IS NULL}),
 * sin exponer ese campo como filtro. Los métodos públicos devuelven records de response
 * mapeados, con coberturas de obra social anidadas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PacienteQueryService extends AbstractFiltroQueryService<Paciente, PacienteCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final PacienteRepository pacienteRepository;
    private final PacienteMapper pacienteMapper;
    private final ObraSocialPacienteRepository obraSocialPacienteRepository;
    private final ObraSocialPacienteMapper obraSocialPacienteMapper;
    private final AlcanceMedicoService alcanceMedicoService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Paciente> getRepository() {

        return pacienteRepository;

    }

    /**
     * Busca el paciente activo que cumple el criteria de filtrado dinámico
     * proporcionado (típicamente un criteria armado con igualdad por {@code id}),
     * junto con sus coberturas de obra social anidadas. A diferencia de
     * {@link #findPacientes}, devuelve un único paciente mapeado (no paginado).
     * Si quien consulta es médico, solo puede ver pacientes con los que tiene turnos.
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code GetPacienteResponse} el paciente encontrado, mapeado y con coberturas
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si ningún
     *         paciente activo cumple el criteria
     */
    public GetPacienteResponse findPacienteByCriteria(PacienteCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando paciente por criteria: {}", criteria);

        //Buscar el paciente por el criteria proporcionado, aplicando scope si es médico
        Paciente pacienteExistente = findOneByCriteria(criteria, usuarioDetails)
                .orElseThrow(() -> {
                    log.warn("No se encontró ningún paciente activo que cumpla el criteria: {}", criteria);
                    return new RecursoNoEncontradoException(getClass(), "PACIENTE_NO_ENCONTRADO",
                            "No existe un paciente activo que cumpla el criteria proporcionado.");
                });

        //Traer las coberturas de obra social anidadas
        List<GetObraSocialAnidadaResponse> obrasSocialesResponse = obraSocialPacienteMapper
                .toGetObraSocialAnidadaResponses(obraSocialPacienteRepository.findByPaciente_IdAndDeletedAtIsNull(pacienteExistente.getId()));

        //Mapear y devolver la respuesta
        GetPacienteResponse getPacienteResponse = pacienteMapper.toGetResponse(pacienteExistente, obrasSocialesResponse);
        return getPacienteResponse;

    }

    /**
     * Lista pacientes activos según el criteria de filtrado dinámico proporcionado,
     * sin incluir coberturas anidadas en el listado (se obtienen bajo demanda para un
     * paciente puntual con {@link #findPacienteByCriteria}). Si quien consulta es médico,
     * solo puede ver pacientes con los que tiene turnos.
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return {@code PageResponse<ListPacienteResponse>} página de pacientes que cumplen el criteria,
     *         mapeados sin coberturas anidadas
     */
    public PageResponse<ListPacienteResponse> findPacientes(PacienteCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Listado de pacientes iniciado: criteria={}, page={}", criteria, pageable);

        //Buscar pacientes que cumplen el criteria, paginados, aplicando scope si es médico
        Page<Paciente> pacientesPagina = findByCriteria(criteria, pageable, usuarioDetails);

        //Mapear y devolver response
        PageResponse<ListPacienteResponse> pageResponse = PageResponse.from(pacientesPagina, pacienteMapper::toListResponse);
        return pageResponse;

    }

    /**
     * Traduce un {@link PacienteCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Paciente>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Paciente> createSpecification(PacienteCriteria criteria) {

        log.debug("Armando specification de pacientes: criteria={}", criteria);

        Specification<Paciente> specification = Specification
                .<Paciente>unrestricted()
                .and((root, query, cb) -> cb.isNull(root.get(Paciente_.deletedAt)));

        if (criteria == null) {
            return specification;
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Paciente_.id));
        }
        if (criteria.getDni() != null) {
            specification = specification.and(buildStringSpecification(criteria.getDni(), Paciente_.dni));
        }
        if (criteria.getNombre() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNombre(), Paciente_.nombre));
        }
        if (criteria.getApellido() != null) {
            specification = specification.and(buildStringSpecification(criteria.getApellido(), Paciente_.apellido));
        }
        if (criteria.getEmail() != null) {
            specification = specification.and(buildStringSpecification(criteria.getEmail(), Paciente_.email));
        }
        if (criteria.getNumeroTelefono() != null) {
            specification = specification.and(buildStringSpecification(criteria.getNumeroTelefono(), Paciente_.numeroTelefono));
        }
        if (criteria.getCreatedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getCreatedDate(), Auditable_.createdDate));
        }
        if (criteria.getLastModifiedDate() != null) {
            specification = specification.and(buildRangeSpecification(criteria.getLastModifiedDate(), Auditable_.lastModifiedDate));
        }

        return specification;

    }

    /**
     * Busca un único paciente aplicando scope: si quien consulta es médico, solo devuelve
     * pacientes con los que tiene turnos (via EXISTS sobre Turno).
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return Optional del paciente encontrado
     */
    private java.util.Optional<Paciente> findOneByCriteria(PacienteCriteria criteria, UsuarioDetails usuarioDetails) {

        log.debug("Buscando un paciente por criteria con scope: criteria={}", criteria);

        Specification<Paciente> specification = createSpecification(criteria);
        UUID medicoIdEfectivo = alcanceMedicoService.resolveMedicoId(usuarioDetails, null);

        if (medicoIdEfectivo != null) {
            UUID medicoIdFinal = medicoIdEfectivo;
            specification = specification.and((root, query, cb) -> {
                jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<Turno> turnoRoot = subquery.from(Turno.class);
                subquery.select(cb.literal(1L));
                subquery.where(
                        cb.equal(turnoRoot.get("paciente"), root),
                        cb.equal(turnoRoot.get("medico").get("id"), medicoIdFinal)
                );
                return cb.exists(subquery);
            });
        }

        return getRepository().findOne(specification);

    }

    /**
     * Lista pacientes aplicando scope: si quien consulta es médico, solo devuelve
     * pacientes con los que tiene turnos (via EXISTS sobre Turno).
     *
     * @param criteria {@code PacienteCriteria} filtros a aplicar
     * @param pageable {@code Pageable} paginación
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @return Page de pacientes encontrados
     */
    private Page<Paciente> findByCriteria(PacienteCriteria criteria, Pageable pageable, UsuarioDetails usuarioDetails) {

        log.debug("Buscando pacientes por criteria con scope: criteria={}, pageable={}", criteria, pageable);

        Specification<Paciente> specification = createSpecification(criteria);
        UUID medicoIdEfectivo = alcanceMedicoService.resolveMedicoId(usuarioDetails, null);

        if (medicoIdEfectivo != null) {
            UUID medicoIdFinal = medicoIdEfectivo;
            specification = specification.and((root, query, cb) -> {
                jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<Turno> turnoRoot = subquery.from(Turno.class);
                subquery.select(cb.literal(1L));
                subquery.where(
                        cb.equal(turnoRoot.get("paciente"), root),
                        cb.equal(turnoRoot.get("medico").get("id"), medicoIdFinal)
                );
                return cb.exists(subquery);
            });
        }

        return getRepository().findAll(specification, pageable);

    }

    //endregion

}
