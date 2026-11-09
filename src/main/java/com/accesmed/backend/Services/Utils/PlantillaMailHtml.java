package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.Clinica;
import lombok.Builder;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;

/**
 * Plantilla única de los mails que manda el sistema. Arma el HTML (y su versión en texto plano)
 * a partir de un {@link ContenidoMail}: encabezado con el logo de AccesMed, contenido del
 * mensaje y un pie con los datos de la clínica (nombre, dirección si la tiene, teléfono y mail).
 *
 * <p>El HTML usa tablas y estilos en línea a propósito: es lo único que Gmail y Outlook
 * renderizan de forma pareja. El logo no viaja como URL sino como imagen embebida
 * ({@code cid:logo-accesmed}): {@link MailService#enviarMailHtml} la adjunta al mensaje.</p>
 *
 * <p>Todo texto variable (nombres, motivos, links) se escapa antes de entrar al HTML.</p>
 */
public final class PlantillaMailHtml {

    //region ========== Constantes ==========

    /** Content-ID con el que se embebe el logo; el {@code <img>} lo referencia como {@code cid:logo-accesmed}. */
    public static final String CONTENT_ID_LOGO = "logo-accesmed";

    /** Color de acento para mensajes informativos. */
    public static final String ACENTO_INFO = "#20B8F0";

    /** Color de acento para mensajes de confirmación o éxito. */
    public static final String ACENTO_EXITO = "#16A34A";

    /** Color de acento para recordatorios y avisos que piden una acción. */
    public static final String ACENTO_ALERTA = "#D97706";

    /** Color de acento para cancelaciones y bajas. */
    public static final String ACENTO_ERROR = "#DC2626";

    private static final String AZUL_OSCURO = "#0D4285";
    private static final String TEXTO = "#1F2937";
    private static final String TEXTO_SUAVE = "#6B7280";
    private static final String FONDO_PAGINA = "#EEF2F7";
    private static final String FONDO_SUAVE = "#F8FAFC";
    private static final String BORDE = "#E2E8F0";
    private static final String FUENTE = "Arial,'Helvetica Neue',Helvetica,sans-serif";

    //endregion

    private PlantillaMailHtml() {

    }

    //region ========== Contenido ==========

    /**
     * Contenido variable de un mail. Todos los campos son opcionales salvo {@code titulo}: lo que
     * venga en {@code null} (o vacío) simplemente no se dibuja.
     *
     * @param etiqueta {@code String} texto corto en forma de insignia sobre el título (ej. "Turno confirmado")
     * @param colorAcento {@code String} color hexadecimal de la insignia, el recuadro de detalle y el botón;
     *        si es {@code null} se usa {@link #ACENTO_INFO}
     * @param titulo {@code String} título principal del mensaje
     * @param saludo {@code String} saludo inicial (ej. "Hola Juan,")
     * @param parrafos {@code List<String>} párrafos del cuerpo, en orden
     * @param detalles {@code Map<String, String>} pares etiqueta-valor para el recuadro de detalle, en orden
     *        de inserción (conviene un {@code LinkedHashMap})
     * @param textoBoton {@code String} texto del botón de acción
     * @param urlBoton {@code String} destino del botón; sin URL no hay botón
     * @param nota {@code String} aclaración final en letra chica
     */
    @Builder
    public record ContenidoMail(
            String etiqueta,
            String colorAcento,
            String titulo,
            String saludo,
            List<String> parrafos,
            Map<String, String> detalles,
            String textoBoton,
            String urlBoton,
            String nota
    ) {

    }

    //endregion

    //region ========== Métodos ==========

    /**
     * Arma el HTML completo del mail: encabezado con logo, contenido y pie con los datos de la clínica.
     *
     * @param contenidoMail {@code ContenidoMail} contenido del mensaje
     * @param clinica {@code Clinica} datos que van en el pie, o {@code null} para omitir el bloque de la clínica
     * @return {@code String} el documento HTML listo para enviar
     */
    public static String armarHtml(ContenidoMail contenidoMail, Clinica clinica) {

        String acento = hayTexto(contenidoMail.colorAcento()) ? contenidoMail.colorAcento() : ACENTO_INFO;
        StringBuilder html = new StringBuilder(4096);

        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"es\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light\">")
                .append("<title>").append(e(contenidoMail.titulo())).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background-color:").append(FONDO_PAGINA).append(";\">");

        //Texto de vista previa que muestran las bandejas de entrada junto al asunto
        html.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
                .append(e(contenidoMail.titulo())).append("</div>");

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"background-color:").append(FONDO_PAGINA).append(";\"><tr><td align=\"center\" ")
                .append("style=\"padding:24px 12px;\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"width:100%;max-width:600px;background-color:#FFFFFF;border-radius:12px;")
                .append("overflow:hidden;font-family:").append(FUENTE).append(";\">");

        armarEncabezado(html);
        armarCuerpo(html, contenidoMail, acento);
        armarPie(html, clinica);

        html.append("</table></td></tr></table></body></html>");

        String htmlArmado = html.toString();
        return htmlArmado;

    }

    /**
     * Arma la versión en texto plano del mismo mensaje. Va como alternativa del HTML para los
     * clientes de correo que no lo muestran, y mejora la entregabilidad.
     *
     * @param contenidoMail {@code ContenidoMail} contenido del mensaje
     * @param clinica {@code Clinica} datos que van en el pie, o {@code null} para omitir el bloque de la clínica
     * @return {@code String} el mensaje en texto plano
     */
    public static String armarTexto(ContenidoMail contenidoMail, Clinica clinica) {

        StringBuilder texto = new StringBuilder();

        texto.append("AccesMed\n\n").append(contenidoMail.titulo()).append("\n\n");

        if (hayTexto(contenidoMail.saludo())) {
            texto.append(contenidoMail.saludo()).append("\n\n");
        }

        if (contenidoMail.parrafos() != null) {
            contenidoMail.parrafos().stream().filter(PlantillaMailHtml::hayTexto)
                    .forEach(parrafo -> texto.append(parrafo).append("\n\n"));
        }

        if (contenidoMail.detalles() != null && !contenidoMail.detalles().isEmpty()) {
            contenidoMail.detalles().forEach((etiqueta, valor) ->
                    texto.append(etiqueta).append(": ").append(valor).append("\n"));
            texto.append("\n");
        }

        if (hayTexto(contenidoMail.urlBoton())) {
            texto.append(hayTexto(contenidoMail.textoBoton()) ? contenidoMail.textoBoton() : "Ingresá a este link")
                    .append(":\n").append(contenidoMail.urlBoton()).append("\n\n");
        }

        if (hayTexto(contenidoMail.nota())) {
            texto.append(contenidoMail.nota()).append("\n\n");
        }

        texto.append("--\n");

        if (clinica != null) {
            texto.append(clinica.getNombre()).append("\n");
            if (hayTexto(clinica.getUbicacion())) {
                texto.append("Dirección: ").append(clinica.getUbicacion()).append("\n");
            }
            if (hayTexto(clinica.getTelefono())) {
                texto.append("Teléfono: ").append(clinica.getTelefono()).append("\n");
            }
            if (hayTexto(clinica.getEmail())) {
                texto.append("Mail: ").append(clinica.getEmail()).append("\n");
            }
        }

        texto.append("Mensaje automático de AccesMed.");

        String textoArmado = texto.toString();
        return textoArmado;

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    private static void armarEncabezado(StringBuilder html) {

        html.append("<tr><td style=\"background-color:").append(AZUL_OSCURO).append(";padding:20px 32px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td style=\"padding-right:14px;\" valign=\"middle\">")
                .append("<img src=\"cid:").append(CONTENT_ID_LOGO).append("\" width=\"56\" height=\"56\" alt=\"AccesMed\" ")
                .append("style=\"display:block;border:0;border-radius:12px;\"></td>")
                .append("<td valign=\"middle\">")
                .append("<div style=\"font-size:24px;font-weight:bold;color:#FFFFFF;line-height:28px;\">AccesMed</div>")
                .append("<div style=\"font-size:13px;color:#9FD8F5;line-height:18px;\">Gestión de turnos médicos</div>")
                .append("</td></tr></table></td></tr>");

    }

    private static void armarCuerpo(StringBuilder html, ContenidoMail contenidoMail, String acento) {

        html.append("<tr><td style=\"padding:32px 32px 8px 32px;color:").append(TEXTO).append(";\">");

        if (hayTexto(contenidoMail.etiqueta())) {
            html.append("<div style=\"margin-bottom:14px;\"><span style=\"display:inline-block;background-color:")
                    .append(acento).append(";color:#FFFFFF;font-size:12px;font-weight:bold;letter-spacing:0.5px;")
                    .append("text-transform:uppercase;padding:5px 12px;border-radius:999px;\">")
                    .append(e(contenidoMail.etiqueta())).append("</span></div>");
        }

        html.append("<h1 style=\"margin:0 0 16px 0;font-size:22px;line-height:28px;color:").append(AZUL_OSCURO)
                .append(";\">").append(e(contenidoMail.titulo())).append("</h1>");

        if (hayTexto(contenidoMail.saludo())) {
            html.append("<p style=\"margin:0 0 14px 0;font-size:16px;line-height:24px;\">")
                    .append(e(contenidoMail.saludo())).append("</p>");
        }

        if (contenidoMail.parrafos() != null) {
            contenidoMail.parrafos().stream().filter(PlantillaMailHtml::hayTexto).forEach(parrafo ->
                    html.append("<p style=\"margin:0 0 14px 0;font-size:16px;line-height:24px;\">")
                            .append(e(parrafo)).append("</p>"));
        }

        armarDetalles(html, contenidoMail.detalles(), acento);
        armarBoton(html, contenidoMail, acento);

        if (hayTexto(contenidoMail.nota())) {
            html.append("<p style=\"margin:8px 0 14px 0;font-size:13px;line-height:20px;color:").append(TEXTO_SUAVE)
                    .append(";\">").append(e(contenidoMail.nota())).append("</p>");
        }

        html.append("</td></tr>");

    }

    private static void armarDetalles(StringBuilder html, Map<String, String> detalles, String acento) {

        if (detalles == null || detalles.isEmpty()) {
            return;
        }

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"margin:6px 0 20px 0;background-color:").append(FONDO_SUAVE)
                .append(";border-left:4px solid ").append(acento).append(";border-radius:6px;\">");

        detalles.forEach((etiqueta, valor) -> html
                .append("<tr><td style=\"padding:10px 16px 0 16px;font-size:12px;color:").append(TEXTO_SUAVE)
                .append(";text-transform:uppercase;letter-spacing:0.5px;\">").append(e(etiqueta)).append("</td></tr>")
                .append("<tr><td style=\"padding:2px 16px 10px 16px;font-size:16px;font-weight:bold;color:")
                .append(TEXTO).append(";\">").append(e(valor)).append("</td></tr>"));

        html.append("</table>");

    }

    private static void armarBoton(StringBuilder html, ContenidoMail contenidoMail, String acento) {

        if (!hayTexto(contenidoMail.urlBoton())) {
            return;
        }

        String textoBoton = hayTexto(contenidoMail.textoBoton()) ? contenidoMail.textoBoton() : "Continuar";

        //Botón "a prueba de Outlook": una celda con color de fondo en vez de un <a> con padding
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"margin:8px 0 20px 0;\"><tr><td align=\"center\" bgcolor=\"").append(AZUL_OSCURO)
                .append("\" style=\"background-color:").append(AZUL_OSCURO).append(";border-radius:8px;\">")
                .append("<a href=\"").append(e(contenidoMail.urlBoton())).append("\" target=\"_blank\" ")
                .append("style=\"display:inline-block;padding:14px 28px;font-size:16px;font-weight:bold;")
                .append("color:#FFFFFF;text-decoration:none;font-family:").append(FUENTE).append(";\">")
                .append(e(textoBoton)).append("</a></td></tr></table>")
                .append("<p style=\"margin:0 0 14px 0;font-size:12px;line-height:18px;color:").append(TEXTO_SUAVE)
                .append(";\">Si el botón no funciona, copiá y pegá este link en tu navegador:<br>")
                .append("<a href=\"").append(e(contenidoMail.urlBoton())).append("\" style=\"color:").append(acento)
                .append(";word-break:break-all;\">").append(e(contenidoMail.urlBoton())).append("</a></p>");

    }

    private static void armarPie(StringBuilder html, Clinica clinica) {

        html.append("<tr><td style=\"background-color:").append(FONDO_SUAVE).append(";border-top:1px solid ")
                .append(BORDE).append(";padding:22px 32px;font-size:13px;line-height:20px;color:")
                .append(TEXTO_SUAVE).append(";\">");

        if (clinica != null) {
            html.append("<div style=\"font-size:15px;font-weight:bold;color:").append(TEXTO).append(";\">")
                    .append(e(clinica.getNombre())).append("</div>");

            //La dirección solo se muestra si la clínica la tiene cargada
            if (hayTexto(clinica.getUbicacion())) {
                html.append("<div>Dirección: ").append(e(clinica.getUbicacion())).append("</div>");
            }

            if (hayTexto(clinica.getTelefono())) {
                html.append("<div>Teléfono: ").append(e(clinica.getTelefono())).append("</div>");
            }

            if (hayTexto(clinica.getEmail())) {
                html.append("<div>Mail: <a href=\"mailto:").append(e(clinica.getEmail())).append("\" style=\"color:")
                        .append(AZUL_OSCURO).append(";text-decoration:none;\">").append(e(clinica.getEmail()))
                        .append("</a></div>");
            }
        }

        html.append("<div style=\"margin-top:14px;font-size:12px;color:#94A3B8;\">")
                .append("Este es un mensaje automático de AccesMed, por favor no lo respondas.</div>")
                .append("</td></tr>");

    }

    private static boolean hayTexto(String texto) {

        return texto != null && !texto.isBlank();

    }

    private static String e(String texto) {

        //Con encoding UTF-8 solo se escapan los caracteres especiales de HTML: las tildes y la ñ quedan tal cual
        return texto == null ? "" : HtmlUtils.htmlEscape(texto, "UTF-8");

    }

    //endregion

}
