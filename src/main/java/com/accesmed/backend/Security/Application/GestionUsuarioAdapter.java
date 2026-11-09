package com.accesmed.backend.Security.Application;

import com.accesmed.backend.Application.Ports.GestionUsuarioPort;
import com.accesmed.backend.Application.UsuarioApp;
import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Security.Services.DomainServices.CambioMailTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.PasswordResetTokenDomainService;
import com.accesmed.backend.Security.Services.DomainServices.RefreshTokenDomainService;
import com.accesmed.backend.Services.DomainServices.ClinicaDomainService;
import com.accesmed.backend.Services.Utils.MailService;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml.ContenidoMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final ClinicaDomainService clinicaDomainService;

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

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Tu cuenta")
                .colorAcento(PlantillaMailHtml.ACENTO_INFO)
                .titulo("Restablecé tu contraseña")
                .saludo("Hola,")
                .parrafos(List.of("Podés definir una contraseña nueva para tu cuenta de AccesMed."))
                .textoBoton("Definir contraseña nueva")
                .urlBoton(link)
                .nota("El link vence en 1 hora y se puede usar una sola vez. "
                        + "Si no pediste esto, contactá a un administrador.")
                .build();

        mailService.enviarMailConPlantilla(usuario.getMail(), "Restablecé tu contraseña — AccesMed",
                contenidoMail, clinicaDomainService.findClinica());

    }

    @Override
    public void iniciarCambioMail(UUID usuarioId, String mailNuevo) {

        Usuario usuario = usuarioApp.prepararCambioMail(usuarioId, mailNuevo);
        String token = cambioMailTokenDomainService.generarYGuardarCambioMailToken(usuario, mailNuevo);
        String link = frontendBaseUrl + "/confirmar-cambio-mail?token=" + token;

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Tu cuenta")
                .colorAcento(PlantillaMailHtml.ACENTO_INFO)
                .titulo("Confirmá tu mail nuevo")
                .saludo("Hola,")
                .parrafos(List.of("Confirmá este mail para usarlo como acceso a tu cuenta de AccesMed."))
                .textoBoton("Confirmar mail nuevo")
                .urlBoton(link)
                .nota("Tu mail actual sigue funcionando hasta que confirmes. "
                        + "El link vence en 1 hora y se puede usar una sola vez.")
                .build();

        mailService.enviarMailConPlantilla(mailNuevo, "Confirmá tu mail nuevo — AccesMed",
                contenidoMail, clinicaDomainService.findClinica());

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private void enviarMailActivacion(Usuario usuario) {

        String token = passwordResetTokenDomainService.generarYGuardarPasswordResetToken(usuario);
        String link = frontendBaseUrl + "/activar-cuenta?token=" + token;

        ContenidoMail contenidoMail = ContenidoMail.builder()
                .etiqueta("Tu cuenta")
                .colorAcento(PlantillaMailHtml.ACENTO_EXITO)
                .titulo("Activá tu cuenta")
                .saludo("¡Te damos la bienvenida a AccesMed!")
                .parrafos(List.of("Ya creamos tu cuenta. Para empezar a usarla, definí tu contraseña."))
                .textoBoton("Activar mi cuenta")
                .urlBoton(link)
                .nota("El link vence en 1 hora y se puede usar una sola vez.")
                .build();

        mailService.enviarMailConPlantilla(usuario.getMail(), "Activá tu cuenta — AccesMed",
                contenidoMail, clinicaDomainService.findClinica());

    }

    private void enviarMailBajaSiCorresponde(Usuario usuario) {

        if (usuario != null) {
            refreshTokenDomainService.revocarTodosLosRefreshTokensDeUsuario(usuario);

            ContenidoMail contenidoMail = ContenidoMail.builder()
                    .etiqueta("Tu cuenta")
                    .colorAcento(PlantillaMailHtml.ACENTO_ERROR)
                    .titulo("Tu cuenta fue dada de baja")
                    .saludo("Hola,")
                    .parrafos(List.of("Tu acceso al sistema de AccesMed fue desactivado.",
                            "Si creés que es un error, contactá a un administrador."))
                    .build();

            mailService.enviarMailConPlantilla(usuario.getMail(), "Tu cuenta fue dada de baja — AccesMed",
                    contenidoMail, clinicaDomainService.findClinica());
        }

    }

    //endregion

}
