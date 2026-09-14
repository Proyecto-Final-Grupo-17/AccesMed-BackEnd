package com.accesmed.backend.Services.Utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Servicio técnico de envío de correos electrónicos. Inyecta {@code JavaMailSender}
 * de Spring Boot (autoconfigurable desde {@code spring.mail.*} en {@code application.yml})
 * y proporciona métodos genéricos de envío. Acceso desde casos de uso que lo necesiten
 * (ej. reseteo de contraseña, notificaciones de turno).
 *
 * <p>El servicio es agnóstico respecto al contenido y destinatario: es responsabilidad
 * del llamante armar el asunto y cuerpo según su lógica de dominio. No atrapa
 * {@code MailException} (unchecked); los errores burbujean a la capa superior.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;

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
     * Envía un correo con contenido HTML.
     *
     * @param destinatario {@code String} dirección de correo del receptor
     * @param asunto {@code String} línea de asunto
     * @param cuerpoHtml {@code String} cuerpo del mensaje en formato HTML
     * @throws MailPreparationException {@code MailPreparationException} si falla la preparación
     *         o envío del mensaje MIME
     */
    public void enviarMailHtml(String destinatario, String asunto, String cuerpoHtml) {
        log.debug("Enviando mail HTML: destinatario={}, asunto={}", destinatario, asunto);
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException excepcion) {
            throw new MailPreparationException("No se pudo preparar el mail HTML", excepcion);
        }
    }

}
