package com.accesmed.backend.Application;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Services.DomainServices.RolDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Orquesta la parte de DATOS de la asignación/desactivación de usuarios (crear o dar de
 * baja el {@code Usuario}, asignar el rol de sistema correspondiente). Uso interno: lo
 * invoca el adaptador de {@code Security} que implementa {@code GestionUsuarioPort},
 * que además se encarga de generar el token de activación y mandar el mail — esta clase
 * no sabe nada de eso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioApp {

    //region ========== Dependencias o inyecciones ==========

    private final UsuarioDomainService usuarioDomainService;
    private final UsuarioRolDomainService usuarioRolDomainService;
    private final RolDomainService rolDomainService;
    private final PasswordEncoder passwordEncoder;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea un usuario pendiente de activación para un médico o un admin (uno de los dos
     * ids viene, el otro es {@code null}), dando de baja el usuario anterior si ya
     * tenía uno activo, y asignando el rol de sistema correspondiente ("Medico" o
     * "Admin").
     *
     * @param medicoId {@code UUID} id del médico, o {@code null} si es para un admin
     * @param adminId {@code UUID} id del admin, o {@code null} si es para un médico
     * @param mail {@code String} mail de login
     * @return {@code Usuario} el usuario recién creado, pendiente de activación
     */
    @Transactional
    public Usuario crearUsuarioPendienteActivacion(UUID medicoId, UUID adminId, String mail) {

        log.info("Creación de usuario iniciada: medicoId={}, adminId={}", medicoId, adminId);

        // Dar de baja el usuario anterior de ese médico/admin, si tenía uno activo
        Usuario usuarioAnterior = medicoId != null
                ? usuarioDomainService.findUsuarioActivoByMedicoId(medicoId).orElse(null)
                : usuarioDomainService.findUsuarioActivoByAdminId(adminId).orElse(null);

        if (usuarioAnterior != null) {
            usuarioDomainService.softDeleteUsuario(usuarioAnterior, "Reemplazado por una nueva asignación de usuario.");
        }

        // Crear el usuario nuevo, con un password no utilizable hasta que se active
        Usuario usuarioNuevo = new Usuario();
        usuarioNuevo.setMail(mail);
        usuarioNuevo.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

        if (medicoId != null) {
            Medico medicoReferencia = new Medico();
            medicoReferencia.setId(medicoId);
            usuarioNuevo.setMedico(medicoReferencia);
        } else {
            Admin adminReferencia = new Admin();
            adminReferencia.setId(adminId);
            usuarioNuevo.setAdmin(adminReferencia);
        }

        Usuario usuarioGuardado = usuarioDomainService.saveUsuario(usuarioNuevo);

        // Asignar el rol de sistema correspondiente
        String nombreRol = medicoId != null ? "Medico" : "Admin";
        Rol rol = rolDomainService.findRolActivoByNombre(nombreRol);
        usuarioRolDomainService.validateAsignacionRol(usuarioGuardado, rol);

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuarioGuardado);
        usuarioRol.setRol(rol);
        usuarioRol.setFechaInicioVigencia(ZonedDateTime.now());
        usuarioRolDomainService.saveUsuarioRol(usuarioRol);

        return usuarioGuardado;

    }

    /**
     * Da de baja el usuario activo vinculado a un médico, si existe.
     *
     * @param medicoId {@code UUID} id del médico
     * @return {@code Usuario} el usuario dado de baja, o {@code null} si no tenía ninguno activo
     */
    @Transactional
    public Usuario desactivarUsuarioPorMedico(UUID medicoId) {
        return desactivarUsuario(usuarioDomainService.findUsuarioActivoByMedicoId(medicoId).orElse(null));
    }

    /**
     * Da de baja el usuario activo vinculado a un admin, si existe.
     *
     * @param adminId {@code UUID} id del admin
     * @return {@code Usuario} el usuario dado de baja, o {@code null} si no tenía ninguno activo
     */
    @Transactional
    public Usuario desactivarUsuarioPorAdmin(UUID adminId) {
        return desactivarUsuario(usuarioDomainService.findUsuarioActivoByAdminId(adminId).orElse(null));
    }

    /**
     * Busca un usuario activo por su identificador.
     *
     * @param id {@code UUID} identificador del usuario
     * @return {@code Usuario} el usuario activo
     */
    @Transactional(readOnly = true)
    public Usuario findUsuarioActivo(UUID id) {
        return usuarioDomainService.findUsuarioActivoById(id);
    }

    /**
     * Da de baja un usuario directamente (sin que implique dar de baja al médico o admin
     * vinculado). El motivo es libre; si viene vacío se usa uno por default.
     *
     * @param id {@code UUID} identificador del usuario
     * @param motivo {@code String} motivo de la baja, o {@code null}/vacío para usar el default
     * @return {@code Usuario} el usuario dado de baja
     */
    @Transactional
    public Usuario softDeleteUsuarioDirecto(UUID id, String motivo) {

        log.info("Baja directa de usuario iniciada: id={}", id);

        Usuario usuarioExistente = usuarioDomainService.findUsuarioActivoById(id);

        String motivoAplicado = StringUtils.hasText(motivo) ? motivo : "Baja manual por SuperAdmin.";
        usuarioDomainService.softDeleteUsuario(usuarioExistente, motivoAplicado);

        return usuarioExistente;

    }

    /**
     * Valida las precondiciones para cambiar el mail de un usuario (activo, mail nuevo
     * distinto del actual y no usado por otro usuario activo) sin aplicar el cambio
     * todavía — el mail solo se actualiza cuando se confirma el token correspondiente.
     *
     * @param id {@code UUID} identificador del usuario
     * @param mailNuevo {@code String} mail nuevo propuesto
     * @return {@code Usuario} el usuario, sin modificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el mail nuevo es igual
     *         al actual o ya está en uso por otro usuario activo
     */
    @Transactional(readOnly = true)
    public Usuario prepararCambioMail(UUID id, String mailNuevo) {

        log.info("Preparando cambio de mail: id={}", id);

        Usuario usuarioExistente = usuarioDomainService.findUsuarioActivoById(id);
        validateMailNuevoDisponible(usuarioExistente, mailNuevo);

        return usuarioExistente;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private void validateMailNuevoDisponible(Usuario usuario, String mailNuevo) {

        if (mailNuevo.equalsIgnoreCase(usuario.getMail())) {
            log.warn("Mail nuevo igual al actual: usuarioId={}", usuario.getId());
            throw new ReglaNegocioException(getClass(), "MAIL_YA_REGISTRADO",
                    "El mail nuevo debe ser distinto del mail actual.");
        }

        usuarioDomainService.findUsuarioActivoByMail(mailNuevo).ifPresent(otro -> {
            log.warn("Mail nuevo ya registrado por otro usuario activo: mailNuevo={}", mailNuevo);
            throw new ReglaNegocioException(getClass(), "MAIL_YA_REGISTRADO",
                    "El mail nuevo ya está en uso por otro usuario.");
        });

    }

    private Usuario desactivarUsuario(Usuario usuario) {
        if (usuario != null) {
            usuarioDomainService.softDeleteUsuario(usuario, "Baja de la persona vinculada.");
        }
        return usuario;
    }

    //endregion

}
