package com.accesmed.backend.Security.Config;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Domain.UsuarioRol;
import com.accesmed.backend.Services.DomainServices.AdminDomainService;
import com.accesmed.backend.Services.DomainServices.RolDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioDomainService;
import com.accesmed.backend.Services.DomainServices.UsuarioRolDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;

/**
 * Bootstrap del primer usuario {@code SuperAdmin}. El rol de sistema {@code SuperAdmin} lo
 * siembra Liquibase, pero ningún flujo de la app puede asignarlo (ver
 * {@code UsuarioRolDomainService.validateAsignacionRol}), así que sin este arranque una base
 * recién migrada no tendría a nadie con acceso total.
 *
 * <p>Corre en cada arranque, después de las migraciones, y es idempotente: si ya existe un
 * usuario activo con el mail configurado no toca nada. La contraseña llega solo por
 * configuración ({@code ACCESMED_SUPERADMIN_PASSWORD}) y nunca vive en el código ni en
 * el repositorio; si falta, el arranque sigue y se deja un aviso.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminInicializador implements ApplicationRunner {

    //region ========== Constantes ==========

    private static final String ROL_SUPERADMIN = "SuperAdmin";
    private static final String NOMBRE_SUPERADMIN = "Super";
    private static final String APELLIDO_SUPERADMIN = "Admin";
    private static final String DNI_SUPERADMIN = "00000001";

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final AdminDomainService adminDomainService;
    private final UsuarioDomainService usuarioDomainService;
    private final UsuarioRolDomainService usuarioRolDomainService;
    private final RolDomainService rolDomainService;
    private final PasswordEncoder passwordEncoder;

    @Value("${accesmed.superadmin.mail}")
    private String mailSuperAdmin;

    @Value("${accesmed.superadmin.password:}")
    private String passwordSuperAdmin;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea el {@code Admin}, su {@code Usuario} y la asignación del rol {@code SuperAdmin}
     * en una única transacción, salvo que la contraseña no esté configurada o que el usuario
     * ya exista.
     *
     * @param args {@code ApplicationArguments} argumentos de arranque (no se usan)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (!StringUtils.hasText(passwordSuperAdmin)) {
            log.warn("No se creó el SuperAdmin: falta la variable ACCESMED_SUPERADMIN_PASSWORD.");
            return;
        }

        if (usuarioDomainService.findUsuarioActivoByMail(mailSuperAdmin).isPresent()) {
            log.debug("El SuperAdmin ya existe, no se hace nada: mail={}", mailSuperAdmin);
            return;
        }

        Rol rolSuperAdmin = rolDomainService.findRolActivoByNombre(ROL_SUPERADMIN);
        adminDomainService.validateDniIsUnique(DNI_SUPERADMIN);

        Admin adminNuevo = new Admin();
        adminNuevo.setNombre(NOMBRE_SUPERADMIN);
        adminNuevo.setApellido(APELLIDO_SUPERADMIN);
        adminNuevo.setDni(DNI_SUPERADMIN);
        adminNuevo.setEmail(mailSuperAdmin);
        Admin adminGuardado = adminDomainService.saveAdmin(adminNuevo);

        Usuario usuarioNuevo = new Usuario();
        usuarioNuevo.setMail(mailSuperAdmin);
        usuarioNuevo.setPasswordHash(passwordEncoder.encode(passwordSuperAdmin));
        usuarioNuevo.setAdmin(adminGuardado);
        Usuario usuarioGuardado = usuarioDomainService.saveUsuario(usuarioNuevo);

        UsuarioRol usuarioRolNuevo = new UsuarioRol();
        usuarioRolNuevo.setUsuario(usuarioGuardado);
        usuarioRolNuevo.setRol(rolSuperAdmin);
        usuarioRolNuevo.setFechaInicioVigencia(ZonedDateTime.now());
        usuarioRolDomainService.saveUsuarioRol(usuarioRolNuevo);

        log.info("SuperAdmin creado en el arranque: mail={}", mailSuperAdmin);

    }

    //endregion

}
