package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml.ContenidoMail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * Servicio técnico de envío de correos electrónicos. Inyecta {@code JavaMailSender}
 * de Spring Boot (autoconfigurable desde {@code spring.mail.*} en {@code application.yml})
 * y proporciona métodos genéricos de envío. Acceso desde casos de uso que lo necesiten
 * (ej. reseteo de contraseña, notificaciones de turno).
 *
 * <p>El servicio es agnóstico respecto al contenido y destinatario: es responsabilidad
 * del llamante armar el asunto y cuerpo según su lógica de dominio (para el HTML, con
 * {@link PlantillaMailHtml}). No atrapa {@code MailException} (unchecked); los errores
 * burbujean a la capa superior.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    //region ========== Dependencias o inyecciones ==========

    private static final String NOMBRE_REMITENTE = "AccesMed";
    private static final String RUTA_LOGO = "mail/logo-accesmed.png";

    private final JavaMailSender javaMailSender;

    /** Casilla desde la que se envía (la cuenta SMTP); se usa además como remitente visible. */
    @Value("${spring.mail.username:}")
    private String remitente;

    //endregion

    //region ========== Métodos ==========

    /**
     * Envía un correo de texto plano.
     *
     * @param destinatario {@code String} dirección de correo del receptor
     * @param asunto {@code String} línea de asunto
     * @param cuerpo {@code String} cuerpo del mensaje (texto plano)
     */
    public void enviarMail(String destinatario, String asunto, String cuerpo) {
        log.debug("Enviando mail: destinatario={}, asunto={}", destinatario, asunto);
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        javaMailSender.send(mensaje);
    }

    /**
     * Arma un mail con la plantilla de AccesMed ({@link PlantillaMailHtml}: logo, contenido y datos
     * de la clínica en el pie) y lo envía en HTML con su alternativa en texto plano. Es el camino
     * que usan todos los mails del sistema, para que se vean parejos.
     *
     * @param destinatario {@code String} dirección de correo del receptor
     * @param asunto {@code String} línea de asunto
     * @param contenidoMail {@code ContenidoMail} contenido del mensaje
     * @param clinica {@code Clinica} datos que van en el pie, o {@code null} para omitirlos
     * @throws MailPreparationException {@code MailPreparationException} si falla la preparación
     *         o envío del mensaje MIME
     */
    public void enviarMailConPlantilla(String destinatario, String asunto, ContenidoMail contenidoMail,
                                       Clinica clinica) {

        String cuerpoHtml = PlantillaMailHtml.armarHtml(contenidoMail, clinica);
        String cuerpoTexto = PlantillaMailHtml.armarTexto(contenidoMail, clinica);

        enviarMailHtml(destinatario, asunto, cuerpoHtml, cuerpoTexto);

    }

    /**
     * Envía un correo con contenido HTML y una alternativa en texto plano. Sale con el nombre
     * visible "AccesMed" y, si el HTML referencia el logo ({@code cid:logo-accesmed}), lo adjunta
     * embebido para que se vea sin depender de que el cliente cargue imágenes remotas.
     *
     * @param destinatario {@code String} dirección de correo del receptor
     * @param asunto {@code String} línea de asunto
     * @param cuerpoHtml {@code String} cuerpo del mensaje en formato HTML
     * @param cuerpoTexto {@code String} versión en texto plano del mismo mensaje
     * @throws MailPreparationException {@code MailPreparationException} si falla la preparación
     *         o envío del mensaje MIME
     */
    public void enviarMailHtml(String destinatario, String asunto, String cuerpoHtml, String cuerpoTexto) {

        log.debug("Enviando mail HTML: destinatario={}, asunto={}", destinatario, asunto);

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            if (remitente != null && !remitente.isBlank()) {
                helper.setFrom(remitente, NOMBRE_REMITENTE);
            }

            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoTexto, cuerpoHtml);

            //El logo va como parte del mensaje: las imágenes remotas suelen bloquearse por defecto
            if (cuerpoHtml.contains("cid:" + PlantillaMailHtml.CONTENT_ID_LOGO)) {
                helper.addInline(PlantillaMailHtml.CONTENT_ID_LOGO, new ClassPathResource(RUTA_LOGO), "image/png");
            }

            javaMailSender.send(mimeMessage);
        } catch (MessagingException | UnsupportedEncodingException excepcion) {
            throw new MailPreparationException("No se pudo preparar el mail HTML", excepcion);
        }

    }

    //endregion

}
