package com.accesmed.backend.Security.Application;

import com.accesmed.backend.Application.Ports.GestionUsuarioPort;
import com.accesmed.backend.Application.UsuarioApp;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Services.DomainServices.CambioMailTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.PasswordResetTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.RefreshTokenDomainService;
import com.accesmed.backend.Services.Utils.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementación de {@link GestionUsuarioPort} para el núcleo: delega la parte de datos
 * en {@link UsuarioApp} y agrega lo propio de {@code Security} — generar el token de
 * activación y mandar el mail correspondiente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GestionUsuarioAdapter implements GestionUsuarioPort {

    //region ========== Dependencias o inyecciones ==========

    private final UsuarioApp usuarioApp;
    private final PasswordResetTokenDomainService passwordResetTokenDomainService;
    private final CambioMailTokenDomainService cambioMailTokenDomainService;
    private final RefreshTokenDomainService refreshTokenDomainService;
    private final MailService mailService;

    @Value("${accesmed.frontend.base-url}")
    private String frontendBaseUrl;

    //endregion

    //region ========== Métodos ==========

    @Override
    public void asignarUsuarioAMedico(UUID medicoId, String mail) {
        Usuario usuario = usuarioApp.crearUsuarioPendienteActivacion(medicoId, null, mail);
        enviarMailActivacion(usuario);
    }

    @Override
    public void asignarUsuarioAAdmin(UUID adminId, String mail) {
        Usuario usuario = usuarioApp.crearUsuarioPendienteActivacion(null, adminId, mail);
        enviarMailActivacion(usuario);
    }

    @Override
    public void desactivarUsuarioDeMedico(UUID medicoId) {
        enviarMailBajaSiCorresponde(usuarioApp.desactivarUsuarioPorMedico(medicoId));
    }

    @Override
    public void desactivarUsuarioDeAdmin(UUID adminId) {
        enviarMailBajaSiCorresponde(usuarioApp.desactivarUsuarioPorAdmin(adminId));
    }

    @Override
    public void desactivarUsuarioDirecto(UUID usuarioId, String motivo) {
        enviarMailBajaSiCorresponde(usuarioApp.softDeleteUsuarioDirecto(usuarioId, motivo));
    }

    @Override
    public void dispararResetContrasena(UUID usuarioId) {
        Usuario usuario = usuarioApp.findUsuarioActivo(usuarioId);
        String token = passwordResetTokenDomainService.generarYGuardarPasswordResetToken(usuario);
        String link = frontendBaseUrl + "/restablecer-contrasena?token=" + token;
        mailService.enviarMail(usuario.getMail(), "Restablecé tu contraseña — AccesMed",
                "Ingresá a este link para definir una contraseña nueva:\n\n" + link
                        + "\n\nSi no pediste esto, contactá a un administrador.");
    }

    @Override
    public void iniciarCambioMail(UUID usuarioId, String mailNuevo) {
        Usuario usuario = usuarioApp.prepararCambioMail(usuarioId, mailNuevo);
        String token = cambioMailTokenDomainService.generarYGuardarCambioMailToken(usuario, mailNuevo);
        String link = frontendBaseUrl + "/confirmar-cambio-mail?token=" + token;
        mailService.enviarMail(mailNuevo, "Confirmá tu mail nuevo — AccesMed",
                "Ingresá a este link para confirmar tu mail nuevo de acceso a AccesMed:\n\n" + link
                        + "\n\nTu mail actual sigue funcionando hasta que confirmes.");
    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private void enviarMailActivacion(Usuario usuario) {
        String token = passwordResetTokenDomainService.generarYGuardarPasswordResetToken(usuario);
        String link = frontendBaseUrl + "/activar-cuenta?token=" + token;
        mailService.enviarMail(usuario.getMail(), "Activá tu cuenta — AccesMed",
                "Ingresá a este link para activar tu cuenta y definir tu contraseña:\n\n" + link);
    }

    private void enviarMailBajaSiCorresponde(Usuario usuario) {
        if (usuario != null) {
            refreshTokenDomainService.revocarTodosLosRefreshTokensDeUsuario(usuario);
            mailService.enviarMail(usuario.getMail(), "Tu cuenta fue dada de baja — AccesMed",
                    "Tu acceso al sistema de AccesMed fue desactivado. Si creés que es un error, "
                            + "contactá a un administrador.");
        }
    }

    //endregion

}
