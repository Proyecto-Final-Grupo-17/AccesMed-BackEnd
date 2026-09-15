package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Repositories.UsuarioRolRepository;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code UsuarioRol}.
 * Encapsula la asignación y revocación de roles, con validaciones de negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioRolDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final UsuarioRolRepository usuarioRolRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una asignación de rol en la base de datos.
     *
     * @param usuarioRol {@code UsuarioRol} entidad a persistir
     * @return {@code UsuarioRol} la asignación guardada
     */
    public UsuarioRol saveUsuarioRol(UsuarioRol usuarioRol) {

        log.debug("Guardando asignación de rol: usuarioId={}, rolId={}",
                usuarioRol.getUsuario().getId(), usuarioRol.getRol().getId());

        return usuarioRolRepository.save(usuarioRol);

    }

    /**
     * Busca las asignaciones de rol vigentes de un usuario (evaluadas al instante actual).
     *
     * @param usuarioId {@code UUID} identificador del usuario
     * @return {@code List<UsuarioRol>} las asignaciones vigentes
     */
    public List<UsuarioRol> findVigentesByUsuarioId(UUID usuarioId) {

        log.debug("Buscando asignaciones vigentes de usuario: usuarioId={}", usuarioId);

        return usuarioRolRepository.findVigentesByUsuarioId(usuarioId, ZonedDateTime.now());

    }

    /**
     * Valida las precondiciones de negocio para asignar un rol a un usuario.
     * Aplica dos guardas en orden:
     * <ol>
     *   <li>El rol SuperAdmin no puede asignarse mediante la aplicación (incondicional).</li>
     *   <li>Si el rol es "Medico", el usuario debe tener un médico vinculado.</li>
     *   <li>Si el rol es otro (distinto de "Medico"), el usuario debe tener un admin vinculado.</li>
     * </ol>
     *
     * @param usuario {@code Usuario} usuario al que se asigna el rol
     * @param rol {@code Rol} rol a asignar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si alguna validación falla
     */
    public void validateAsignacionRol(Usuario usuario, Rol rol) {

        // Guarda 1: SuperAdmin no es asignable
        if ("SuperAdmin".equals(rol.getNombre())) {
            log.warn("No se puede asignar el rol SuperAdmin: usuarioId={}", usuario.getId());
            throw new ReglaNegocioException(getClass(), "SUPERADMIN_NO_ASIGNABLE",
                    "El rol SuperAdmin no puede asignarse mediante la aplicación.");
        }

        // Guarda 2: Si es rol Medico, debe tener médico vinculado
        if ("Medico".equals(rol.getNombre())) {
            if (usuario.getMedico() == null) {
                log.warn("No se puede asignar rol Medico: usuario sin médico vinculado, usuarioId={}", usuario.getId());
                throw new ReglaNegocioException(getClass(), "USUARIO_SIN_MEDICO_VINCULADO",
                        "No se puede asignar el rol Medico a un usuario sin médico vinculado.");
            }
        } else {
            // Guarda 3: Si es otro rol (incluidos los dinámicos), debe tener admin vinculado
            if (usuario.getAdmin() == null) {
                log.warn("No se puede asignar rol no-Medico: usuario sin admin vinculado, usuarioId={}, rol={}",
                        usuario.getId(), rol.getNombre());
                throw new ReglaNegocioException(getClass(), "USUARIO_SIN_ADMIN_VINCULADO",
                        "Este rol solo puede asignarse a un usuario vinculado a un Admin.");
            }
        }

    }

    /**
     * Revoca una asignación de rol cerrando su fecha de fin de vigencia.
     * Valida que no sea el rol SuperAdmin antes de revocar.
     *
     * @param usuarioRol {@code UsuarioRol} asignación a revocar
     * @return {@code UsuarioRol} la asignación revocada (con {@code fechaFinVigencia} actualizada)
     * @throws ReglaNegocioException {@code ReglaNegocioException} si se intenta revocar
     *         una asignación de SuperAdmin
     */
    public UsuarioRol revocarUsuarioRol(UsuarioRol usuarioRol) {

        // Guarda: SuperAdmin no puede ser revocado
        if ("SuperAdmin".equals(usuarioRol.getRol().getNombre())) {
            log.warn("No se puede revocar el rol SuperAdmin: usuarioRolId={}", usuarioRol.getId());
            throw new ReglaNegocioException(getClass(), "SUPERADMIN_NO_ASIGNABLE",
                    "El rol SuperAdmin no puede asignarse mediante la aplicación.");
        }

        log.debug("Revocando asignación de rol: usuarioRolId={}, rol={}",
                usuarioRol.getId(), usuarioRol.getRol().getNombre());

        usuarioRol.setFechaFinVigencia(ZonedDateTime.now());

        return saveUsuarioRol(usuarioRol);

    }

    //endregion

}
