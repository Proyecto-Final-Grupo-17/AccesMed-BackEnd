package com.accesmed.backend.Controllers.Errors;

import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de {@link GlobalExceptionHandler}: los errores que salen al front están en español,
 * explican qué corregir y no exponen identificadores internos ni detalle del framework.
 */
class GlobalExceptionHandlerTest {

    private static final UUID ID_INTERNO = UUID.fromString("0ed47a50-0dca-4548-a02c-321b9d8884c7");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ControllerDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void reglaDeNegocio_devuelveElMensajeDelServiceSinDetalleTecnico() throws Exception {
        mockMvc.perform(get("/prueba/regla"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REGLA_DE_PRUEBA"))
                .andExpect(jsonPath("$.mensaje").value("No se puede realizar la operación."));
    }

    @Test
    void fechaConFormatoInvalido_400ExplicaElCampoYElFormatoEsperado() throws Exception {
        mockMvc.perform(post("/prueba/fecha").contentType(APPLICATION_JSON)
                        .content("{\"fechaInicioVigencia\": \"15/03/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.mensaje", containsString("fecha inicio vigencia")))
                .andExpect(jsonPath("$.mensaje", containsString("AAAA-MM-DD")))
                .andExpect(jsonPath("$.mensaje", not(containsString("java.time"))))
                .andExpect(jsonPath("$.mensaje", not(containsString("LocalDate"))));
    }

    @Test
    void jsonMalFormado_400SinDetalleDelDeserializador() throws Exception {
        mockMvc.perform(post("/prueba/fecha").contentType(APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.mensaje", not(containsString("Jackson"))))
                .andExpect(jsonPath("$.mensaje", not(containsString("line:"))));
    }

    @Test
    void campoObligatorioAusente_422ConNombreLegibleDelCampo() throws Exception {
        mockMvc.perform(post("/prueba/fecha").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0]", containsString("Fecha inicio vigencia")));
    }

    @Test
    void idDeRutaQueNoEsUuid_400SinEchoDeClasesInternas() throws Exception {
        mockMvc.perform(get("/prueba/recurso/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("identificador válido")))
                .andExpect(jsonPath("$.mensaje", not(containsString("java.util.UUID"))));
    }

    @Test
    void errorInesperado_500NoExponeElMensajeDeLaExcepcion() throws Exception {
        mockMvc.perform(get("/prueba/falla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INESPERADO"))
                .andExpect(jsonPath("$.mensaje", not(containsString(ID_INTERNO.toString()))))
                .andExpect(jsonPath("$.errores[0]", not(containsString(ID_INTERNO.toString()))));
    }

    @Test
    void metodoNoSoportado_405EnEspanol() throws Exception {
        mockMvc.perform(post("/prueba/regla"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    record FechaRequest(@NotNull LocalDate fechaInicioVigencia) {

    }

    @RestController
    static class ControllerDePrueba {

        @GetMapping("/prueba/regla")
        public String regla() {
            throw new ReglaNegocioException(getClass(), "REGLA_DE_PRUEBA", "No se puede realizar la operación.");
        }

        @PostMapping("/prueba/fecha")
        public String fecha(@Valid @RequestBody FechaRequest fechaRequest) {
            return "ok";
        }

        @GetMapping("/prueba/recurso/{id}")
        public String recurso(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/prueba/falla")
        public String falla() {
            throw new IllegalStateException("Fallo interno con id " + ID_INTERNO);
        }

    }

}
