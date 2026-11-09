package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Services.Utils.PlantillaMailHtml.ContenidoMail;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios de {@link PlantillaMailHtml}: el logo, los datos de la clínica (la dirección solo
 * si existe), el escapado de texto variable y la versión en texto plano.
 */
class PlantillaMailHtmlTest {

    private static Clinica armarClinica(String ubicacion) {
        Clinica clinica = new Clinica();
        clinica.setNombre("Clínica AccesMed");
        clinica.setUbicacion(ubicacion);
        clinica.setTelefono("+54 261 555-0000");
        clinica.setEmail("contacto@accesmed.com");
        return clinica;
    }

    private static ContenidoMail armarContenido() {
        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Código del turno", "TURNO_12345678");
        return ContenidoMail.builder()
                .etiqueta("Turno confirmado")
                .colorAcento(PlantillaMailHtml.ACENTO_EXITO)
                .titulo("Tu turno está confirmado")
                .saludo("Hola Juan,")
                .parrafos(List.of("Te esperamos."))
                .detalles(detalles)
                .build();
    }

    @Test
    void armarHtml_incluyeElLogoEmbebidoYElContenido() {
        String html = PlantillaMailHtml.armarHtml(armarContenido(), armarClinica("Av. San Martín 123"));

        assertTrue(html.contains("cid:" + PlantillaMailHtml.CONTENT_ID_LOGO));
        assertTrue(html.contains("Tu turno está confirmado"));
        assertTrue(html.contains("TURNO_12345678"));
        assertTrue(html.contains("#16A34A"));
    }

    @Test
    void armarHtml_clinicaConDireccion_muestraTodosLosDatosDeLaClinica() {
        String html = PlantillaMailHtml.armarHtml(armarContenido(), armarClinica("Av. San Martín 123"));

        assertTrue(html.contains("Clínica AccesMed"));
        assertTrue(html.contains("Dirección: Av. San Martín 123"));
        assertTrue(html.contains("+54 261 555-0000"));
        assertTrue(html.contains("contacto@accesmed.com"));
    }

    @Test
    void armarHtml_clinicaSinDireccion_noMuestraLaLineaDeDireccion() {
        String htmlSinUbicacion = PlantillaMailHtml.armarHtml(armarContenido(), armarClinica(null));
        String htmlConUbicacionBlanca = PlantillaMailHtml.armarHtml(armarContenido(), armarClinica("   "));

        assertTrue(htmlSinUbicacion.contains("Clínica AccesMed"));
        assertFalse(htmlSinUbicacion.contains("Dirección"));
        assertFalse(htmlConUbicacionBlanca.contains("Dirección"));
    }

    @Test
    void armarHtml_sinClinica_noFallaYNoMuestraBloqueDeClinica() {
        String html = PlantillaMailHtml.armarHtml(armarContenido(), null);

        assertTrue(html.contains("Tu turno está confirmado"));
        assertFalse(html.contains("Dirección"));
    }

    @Test
    void armarHtml_escapaElTextoVariable() {
        ContenidoMail contenidoMail = ContenidoMail.builder()
                .titulo("Hola")
                .saludo("Hola <script>alert(1)</script> & compañía,")
                .build();

        String html = PlantillaMailHtml.armarHtml(contenidoMail, armarClinica("Calle <b>1</b>"));

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<b>1</b>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&amp; compañía"));
    }

    @Test
    void armarHtml_conBoton_incluyeElLinkEnElBotonYComoRespaldo() {
        ContenidoMail contenidoMail = ContenidoMail.builder()
                .titulo("Activá tu cuenta")
                .textoBoton("Activar mi cuenta")
                .urlBoton("http://localhost:5173/activar-cuenta?token=abc&x=1")
                .build();

        String html = PlantillaMailHtml.armarHtml(contenidoMail, null);

        assertTrue(html.contains("Activar mi cuenta"));
        assertTrue(html.contains("href=\"http://localhost:5173/activar-cuenta?token=abc&amp;x=1\""));
    }

    @Test
    void armarHtml_sinBoton_noDibujaElBoton() {
        String html = PlantillaMailHtml.armarHtml(armarContenido(), null);

        assertFalse(html.contains("Si el botón no funciona"));
    }

    @Test
    void armarTexto_incluyeContenidoYDatosDeLaClinica() {
        String texto = PlantillaMailHtml.armarTexto(armarContenido(), armarClinica("Av. San Martín 123"));

        assertTrue(texto.contains("Tu turno está confirmado"));
        assertTrue(texto.contains("Código del turno: TURNO_12345678"));
        assertTrue(texto.contains("Dirección: Av. San Martín 123"));
        assertFalse(texto.contains("<"));
    }

}
