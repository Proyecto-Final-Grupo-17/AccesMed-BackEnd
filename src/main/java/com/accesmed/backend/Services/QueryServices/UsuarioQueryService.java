package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Admin_;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Domain.Usuario_;
import com.accesmed.backend.Records.Usuario.Criteria.EstadoUsuarioFiltro;
import com.accesmed.backend.Records.Usuario.Criteria.UsuarioCriteria;
import com.accesmed.backend.Records.Usuario.Response.GetUsuarioResponse;
import com.accesmed.backend.Records.Usuario.Response.ListUsuarioResponse;
import com.accesmed.backend.Repositories.UsuarioRepository;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.UsuarioMapper;
import com.accesmed.backend.Services.QueryServices.Filtering.AbstractFiltroQueryService;
import com.accesmed.backend.Services.QueryServices.Filtering.PageResponse;
import jakarta.persistence.criteria.Root;
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
 * Consultas de lectura para la entidad {@code Usuario}, incluido el filtrado dinámico por
 * {@link UsuarioCriteria} (ver {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). A
 * diferencia del resto de los {@code QueryService} del sistema, expone el filtro de baja
 * lógica ({@code estado}) en vez de excluirla siempre — el SuperAdmin necesita ver también
 * los usuarios inactivos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioQueryService extends AbstractFiltroQueryService<Usuario, UsuarioCriteria> {

    //region ========== Dependencias o inyecciones ==========

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final UsuarioRolDomainService usuarioRolDomainService;

    //endregion

    //region ========== Métodos ==========

    @Override
    protected JpaSpecificationExecutor<Usuario> getRepository() {

        return usuarioRepository;

    }

    /**
     * Obtiene el detalle de un usuario por identificador, esté activo o dado de baja.
     *
     * @param id {@code UUID} identificador del usuario
     * @return {@code GetUsuarioResponse} el usuario encontrado, mapeado
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         un usuario con ese id
     */
    public GetUsuarioResponse findUsuarioById(UUID id) {

        log.debug("Buscando usuario por id: {}", id);

        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el usuario: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "USUARIO_NO_ENCONTRADO",
                            "No se encontró el usuario solicitado.");
                });

        GetUsuarioResponse getUsuarioResponse = usuarioMapper.toGetResponse(usuarioExistente,
                resolverNombre(usuarioExistente), resolverApellido(usuarioExistente), resolverRoles(usuarioExistente));
        return getUsuarioResponse;

    }

    /**
     * Lista usuarios según el criteria de filtrado dinámico proporcionado, incluyendo
     * activos e inactivos según {@link UsuarioCriteria#getEstado()}.
     *
     * @param criteria {@code UsuarioCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code PageResponse<ListUsuarioResponse>} página de usuarios que cumplen el criteria
     */
    public PageResponse<ListUsuarioResponse> findUsuarios(UsuarioCriteria criteria, Pageable pageable) {

        log.debug("Listado de usuarios iniciado: criteria={}, page={}", criteria, pageable);

        Page<Usuario> usuariosPagina = findByCriteria(criteria, pageable);

        PageResponse<ListUsuarioResponse> pageResponse = PageResponse.from(usuariosPagina,
                usuario -> usuarioMapper.toListResponse(usuario,
                        resolverNombre(usuario), resolverApellido(usuario), resolverRoles(usuario)));
        return pageResponse;

    }

    /**
     * Traduce un {@link UsuarioCriteria} a la {@link Specification} equivalente,
     * combinando un fragmento por cada campo filtrable que vino con valor. A diferencia
     * del resto de los {@code createSpecification}, el filtro de baja lógica se arma
     * desde {@code criteria.getEstado()} en vez de excluirse siempre.
     *
     * @param criteria {@code UsuarioCriteria} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<Usuario>} especificación equivalente al criteria
     */
    @Override
    protected Specification<Usuario> createSpecification(UsuarioCriteria criteria) {

        log.debug("Armando specification de usuarios: criteria={}", criteria);

        Specification<Usuario> specification = Specification.unrestricted();

        if (criteria == null) {
            return aplicarEstado(specification, null);
        }

        if (criteria.getId() != null) {
            specification = specification.and(buildSpecification(criteria.getId(), Usuario_.id));
        }
        if (criteria.getMail() != null) {
            specification = specification.and(buildStringSpecification(criteria.getMail(), Usuario_.mail));
        }
        if (criteria.getMedicoId() != null) {
            specification = specification.and(buildSpecification(criteria.getMedicoId(),
                    (Root<Usuario> root) -> root.get(Usuario_.medico).get(Medico_.id)));
        }
        if (criteria.getAdminId() != null) {
            specification = specification.and(buildSpecification(criteria.getAdminId(),
                    (Root<Usuario> root) -> root.get(Usuario_.admin).get(Admin_.id)));
        }

        return aplicarEstado(specification, criteria.getEstado());

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Agrega a la specification el filtro de baja lógica según el estado pedido.
     * {@code null} o {@code ACTIVO} solo trae activos (comportamiento por defecto,
     * consistente con el resto del sistema); {@code INACTIVO} solo dados de baja;
     * {@code TODOS} no filtra por {@code deletedAt}.
     *
     * @param specification {@code Specification<Usuario>} specification de base
     * @param estado {@code EstadoUsuarioFiltro} estado pedido, o {@code null}
     * @return {@code Specification<Usuario>} specification con el filtro de estado aplicado
     */
    private Specification<Usuario> aplicarEstado(Specification<Usuario> specification, EstadoUsuarioFiltro estado) {

        if (estado == EstadoUsuarioFiltro.TODOS) {
            return specification;
        }
        if (estado == EstadoUsuarioFiltro.INACTIVO) {
            return specification.and((root, query, cb) -> cb.isNotNull(root.get(Usuario_.deletedAt)));
        }
        return specification.and((root, query, cb) -> cb.isNull(root.get(Usuario_.deletedAt)));

    }

    private String resolverNombre(Usuario usuario) {
        return usuario.getMedico() != null ? usuario.getMedico().getNombre() : usuario.getAdmin().getNombre();
    }

    private String resolverApellido(Usuario usuario) {
        return usuario.getMedico() != null ? usuario.getMedico().getApellido() : usuario.getAdmin().getApellido();
    }

    private List<String> resolverRoles(Usuario usuario) {
        return usuarioRolDomainService.findVigentesByUsuarioId(usuario.getId()).stream()
                .map(UsuarioRol::getRol)
                .map(rol -> rol.getNombre())
                .toList();
    }

    //endregion

}
