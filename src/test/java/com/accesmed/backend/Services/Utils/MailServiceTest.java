package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml.ContenidoMail;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link MailService#enviarMailConPlantilla}: arma el mensaje MIME real y
 * verifica remitente, destinatario, logo embebido y las dos versiones del cuerpo (HTML y texto).
 */
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private MailService mailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailService, "remitente", "proyectofinal17@gmail.com");
        when(javaMailSender.createMimeMessage()).thenAnswer(invocacion -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void enviarMailConPlantilla_armaMensajeMultipartConLogoEmbebido() throws Exception {
        Clinica clinica = new Clinica();
        clinica.setNombre("Clínica AccesMed");
        clinica.setUbicacion("Av. San Martín 123");

        ContenidoMail contenidoMail = ContenidoMail.builder().titulo("Tu turno está confirmado").build();

        mailService.enviarMailConPlantilla("paciente@example.com", "Turno confirmado", contenidoMail, clinica);

        ArgumentCaptor<MimeMessage> mensajeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(mensajeCaptor.capture());
        MimeMessage mensaje = mensajeCaptor.getValue();
        mensaje.saveChanges();

        Address remitenteEnviado = mensaje.getFrom()[0];
        assertEquals("proyectofinal17@gmail.com", ((InternetAddress) remitenteEnviado).getAddress());
        assertEquals("AccesMed", ((InternetAddress) remitenteEnviado).getPersonal());
        assertEquals("paciente@example.com", mensaje.getAllRecipients()[0].toString());
        assertEquals("Turno confirmado", mensaje.getSubject());

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        mensaje.writeTo(salida);
        String mimeCompleto = salida.toString(StandardCharsets.ISO_8859_1);

        assertTrue(mimeCompleto.contains("multipart/related"), "el logo tiene que ir embebido en el mensaje");
        assertTrue(mimeCompleto.contains("image/png"));
        assertTrue(mimeCompleto.contains("Content-ID: <logo-accesmed>"));
        assertTrue(mimeCompleto.contains("text/html"));
        assertTrue(mimeCompleto.contains("text/plain"));
    }

}
