package com.accesmed.backend.Controllers.Errors;

import com.accesmed.backend.Services.Errors.AccesMedException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Manejador global de excepciones. Traduce cualquier error de la aplicación al contrato
 * único {@code AccesMedError} que ve el front.
 *
 * <p>Regla de fondo: el mensaje que sale al front está escrito para la persona que usa el
 * sistema — nunca incluye identificadores internos (UUID), nombres de clases, de
 * constraints, de tablas ni texto de excepciones del framework. Todo ese detalle técnico
 * queda solo en el log, para depurar.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //region ========== Constantes ==========

    /**
     * Mensajes por defecto de Bean Validation ({@code @NotNull}, {@code @NotBlank},
     * {@code @NotEmpty}) que significan "campo obligatorio", en español y en inglés (por si el
     * locale de la JVM no llegara a aplicarse).
     */
    private static final Set<String> MENSAJES_CAMPO_OBLIGATORIO = Set.of(
            "no debe ser nulo", "no debe estar vacío", "no debe estar en blanco", "no debe ser null",
            "must not be null", "must not be blank", "must not be empty");

    //endregion

    //region ========== Métodos ==========

    /**
     * Traduce cualquier {@code AccesMedException} (y sus subclases) a {@code AccesMedError},
     * usando su código y status HTTP propios. No la loguea en {@code warn}: ya se logueó en el
     * Service que la lanzó, con el detalle técnico completo, justo antes del {@code throw}.
     * Solo deja un {@code debug} con el origen, para poder rastrear qué clase la lanzó.
     *
     * @param accesMedException {@code AccesMedException} excepción de negocio lanzada por un Service
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error traducido al contrato del front
     */
    @ExceptionHandler(AccesMedException.class)
    public ResponseEntity<AccesMedError> handleAccesMedException(AccesMedException accesMedException,
                                                                   HttpServletRequest request) {

        log.debug("Error de negocio {} (HTTP {}) lanzado desde {} en {}", accesMedException.getCodigo(),
                accesMedException.getHttpStatus(), accesMedException.getOrigen(), request.getRequestURI());

        AccesMedError accesMedError = AccesMedError.of(accesMedException.getHttpStatus(),
                accesMedException.getCodigo(), accesMedException.getMessage(),
                errorsOf(accesMedException), request.getRequestURI());

        return ResponseEntity.status(accesMedException.getHttpStatus()).body(accesMedError);

    }

    /**
     * Traduce los errores de Bean Validation ({@code @Valid}) a {@code AccesMedError}, con un
     * mensaje por campo en español. Único caso (junto con {@code ConstraintViolationException})
     * donde el handler loguea: Bean Validation no tiene un Service de origen, la lanza el
     * propio framework en el borde HTTP.
     *
     * @param methodArgumentNotValidException {@code MethodArgumentNotValidException} excepción
     *        lanzada por Bean Validation al fallar {@code @Valid}
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error de validación traducido
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AccesMedError> handleValidation(MethodArgumentNotValidException methodArgumentNotValidException,
                                                            HttpServletRequest request) {

        List<String> errores = methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> mensajeDeCampo(fieldError.getField(), fieldError.getDefaultMessage()))
                .distinct()
                .toList();

        log.warn("Validación fallida en {}: {}", request.getRequestURI(), errores);

        return validationResponse(errores, request);

    }

    /**
     * Traduce las violaciones de constraint a nivel de parámetro o persistencia a
     * {@code AccesMedError}. Mismo criterio que Bean Validation: sin Service de origen, se
     * loguea acá mismo.
     *
     * @param constraintViolationException {@code ConstraintViolationException} excepción de
     *        validación a nivel de parámetro o persistencia
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error de validación traducido
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<AccesMedError> handleConstraintViolation(ConstraintViolationException constraintViolationException,
                                                                     HttpServletRequest request) {

        List<String> errores = constraintViolationException.getConstraintViolations().stream()
                .map(violation -> mensajeDeCampo(ultimoNodo(violation), violation.getMessage()))
                .distinct()
                .toList();

        log.warn("Validación fallida en {}: {}", request.getRequestURI(),
                constraintViolationException.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .toList());

        return validationResponse(errores, request);

    }

    /**
     * Traduce los errores de validación de parámetros de método ({@code @RequestParam},
     * {@code @PathVariable} con constraints) a {@code AccesMedError}.
     *
     * @param handlerMethodValidationException {@code HandlerMethodValidationException} excepción
     *        de validación de los parámetros del handler
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error de validación traducido
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<AccesMedError> handleHandlerMethodValidation(HandlerMethodValidationException handlerMethodValidationException,
                                                                        HttpServletRequest request) {

        List<String> errores = handlerMethodValidationException.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(mensaje -> mensaje != null && !mensaje.isBlank())
                .distinct()
                .toList();

        log.warn("Validación de parámetros fallida en {}: {}", request.getRequestURI(), errores);

        return validationResponse(errores.isEmpty() ? List.of("Alguno de los datos enviados no es válido.") : errores, request);

    }

    /**
     * Traduce un cuerpo de request ilegible ({@code HttpMessageNotReadableException}): JSON mal
     * formado, un valor con formato inválido (fecha, UUID, número, valor de un enum) o un
     * cuerpo ausente. Explica qué campo tiene el problema y qué formato se esperaba, sin
     * exponer el detalle de Jackson (nombres de clases, paquetes, líneas del JSON), que va al log.
     *
     * @param httpMessageNotReadableException {@code HttpMessageNotReadableException} excepción
     *        lanzada al deserializar el cuerpo
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 400 traducido
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AccesMedError> handleMessageNotReadable(HttpMessageNotReadableException httpMessageNotReadableException,
                                                                    HttpServletRequest request) {

        log.warn("Cuerpo de request ilegible en {}: {}", request.getRequestURI(), httpMessageNotReadableException.getMessage());

        String mensajeError = mensajeDeCuerpoIlegible(httpMessageNotReadableException);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(AccesMedError.of(
                HttpStatus.BAD_REQUEST.value(), "SOLICITUD_INVALIDA", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce un parámetro de ruta o de query con un valor que no se puede convertir al tipo
     * esperado (ej. un id que no es un UUID, una fecha mal escrita).
     *
     * @param methodArgumentTypeMismatchException {@code MethodArgumentTypeMismatchException}
     *        excepción lanzada al convertir el parámetro
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 400 traducido
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AccesMedError> handleTypeMismatch(MethodArgumentTypeMismatchException methodArgumentTypeMismatchException,
                                                              HttpServletRequest request) {

        log.warn("Parámetro con tipo inválido en {}: {}", request.getRequestURI(), methodArgumentTypeMismatchException.getMessage());

        String mensajeError = "El valor del parámetro '" + nombreLegible(methodArgumentTypeMismatchException.getName())
                + "' no es válido. Se esperaba " + descripcionDeTipo(methodArgumentTypeMismatchException.getRequiredType()) + ".";

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(AccesMedError.of(
                HttpStatus.BAD_REQUEST.value(), "SOLICITUD_INVALIDA", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce la ausencia de un parámetro de query obligatorio.
     *
     * @param missingServletRequestParameterException {@code MissingServletRequestParameterException}
     *        excepción lanzada por Spring al faltar el parámetro
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 400 traducido
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<AccesMedError> handleMissingParameter(MissingServletRequestParameterException missingServletRequestParameterException,
                                                                  HttpServletRequest request) {

        log.warn("Parámetro obligatorio ausente en {}: {}", request.getRequestURI(), missingServletRequestParameterException.getMessage());

        String mensajeError = "Falta el parámetro obligatorio '"
                + nombreLegible(missingServletRequestParameterException.getParameterName()) + "'.";

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(AccesMedError.of(
                HttpStatus.BAD_REQUEST.value(), "SOLICITUD_INVALIDA", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce el uso de un verbo HTTP no soportado por el endpoint.
     *
     * @param httpRequestMethodNotSupportedException {@code HttpRequestMethodNotSupportedException}
     *        excepción lanzada por Spring
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 405 traducido
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<AccesMedError> handleMethodNotSupported(HttpRequestMethodNotSupportedException httpRequestMethodNotSupportedException,
                                                                    HttpServletRequest request) {

        log.warn("Método HTTP no soportado en {}: {}", request.getRequestURI(), httpRequestMethodNotSupportedException.getMessage());

        String mensajeError = "Esta operación no admite el método " + httpRequestMethodNotSupportedException.getMethod() + ".";

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(AccesMedError.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(), "METODO_NO_PERMITIDO", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce el envío de un cuerpo con un {@code Content-Type} que el endpoint no acepta.
     *
     * @param httpMediaTypeNotSupportedException {@code HttpMediaTypeNotSupportedException}
     *        excepción lanzada por Spring
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 415 traducido
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<AccesMedError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException httpMediaTypeNotSupportedException,
                                                                       HttpServletRequest request) {

        log.warn("Content-Type no soportado en {}: {}", request.getRequestURI(), httpMediaTypeNotSupportedException.getMessage());

        String mensajeError = "El formato de los datos enviados no es válido. Enviá el cuerpo como JSON.";

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(AccesMedError.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "FORMATO_NO_SOPORTADO", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce una ruta que no existe.
     *
     * @param noResourceFoundException {@code NoResourceFoundException} excepción lanzada por Spring
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 404 traducido
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<AccesMedError> handleNoResourceFound(NoResourceFoundException noResourceFoundException,
                                                                 HttpServletRequest request) {

        log.warn("Ruta inexistente: {} {}", request.getMethod(), request.getRequestURI());

        String mensajeError = "La dirección solicitada no existe.";

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AccesMedError.of(
                HttpStatus.NOT_FOUND.value(), "RUTA_NO_ENCONTRADA", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce una violación de integridad de la base de datos que no atajó ninguna validación
     * previa (ej. dos operaciones concurrentes que pisan el mismo período, o una restricción
     * única). Nunca expone el nombre de la constraint ni el mensaje de Postgres: los deja en el
     * log y responde con un texto entendible.
     *
     * @param dataIntegrityViolationException {@code DataIntegrityViolationException} excepción
     *        lanzada por Spring Data / Hibernate
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error 409 traducido
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<AccesMedError> handleDataIntegrityViolation(DataIntegrityViolationException dataIntegrityViolationException,
                                                                        HttpServletRequest request) {

        Throwable causaRaiz = dataIntegrityViolationException.getMostSpecificCause();

        log.warn("Violación de integridad de datos en {}: {}", request.getRequestURI(), causaRaiz.getMessage());

        String mensajeError = mensajeDeIntegridad(causaRaiz.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(AccesMedError.of(
                HttpStatus.CONFLICT.value(), "CONFLICTO_DE_DATOS", mensajeError, List.of(mensajeError),
                request.getRequestURI()));

    }

    /**
     * Traduce el rechazo de autorización ({@code AccessDeniedException}) a
     * {@code AccesMedError} con un mensaje en español. Se loguea porque, aunque es un
     * 4xx de cliente, Spring Security no tiene un Service de origen que lo logueó
     * previamente.
     *
     * @param accessDeniedException {@code AccessDeniedException} excepción de acceso
     *        denegado lanzada por Spring Security
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error de acceso denegado traducido
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AccesMedError> handleAccessDenied(AccessDeniedException accessDeniedException,
                                                             HttpServletRequest request) {

        log.warn("Acceso denegado en {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(AccesMedError.of(
                HttpStatus.FORBIDDEN.value(), "ACCESO_DENEGADO",
                "No tiene permisos para realizar esta acción.",
                List.of("No tiene permisos para realizar esta acción."), request.getRequestURI()));

    }

    /**
     * Traduce los errores de autenticación ({@code AuthenticationException}) a
     * {@code AccesMedError} con un mensaje en español. Se loguea porque Spring Security
     * no tiene un Service de origen que lo logueó previamente. Cubre tanto credenciales
     * inválidas como otros errores de autenticación.
     *
     * @param authenticationException {@code AuthenticationException} excepción de
     *        autenticación lanzada por Spring Security
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error de autenticación traducido
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<AccesMedError> handleAuthenticationException(AuthenticationException authenticationException,
                                                                       HttpServletRequest request) {

        log.warn("Autenticación fallida en {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AccesMedError.of(
                HttpStatus.UNAUTHORIZED.value(), "CREDENCIALES_INVALIDAS",
                "El mail o la contraseña son incorrectos.",
                List.of("El mail o la contraseña son incorrectos."), request.getRequestURI()));

    }

    /**
     * Traduce cualquier excepción no controlada a {@code AccesMedError} con un mensaje
     * genérico, sin filtrar detalles internos al front. El stack trace completo queda en el log.
     *
     * @param exception {@code Exception} excepción no controlada
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error genérico traducido
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AccesMedError> handleGenericException(Exception exception, HttpServletRequest request) {

        log.error("Error inesperado en {}", request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AccesMedError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "ERROR_INESPERADO",
                "Ocurrió un error inesperado. Intentá de nuevo en unos minutos y, si el problema persiste, contactá a soporte.",
                List.of("Ocurrió un error inesperado."), request.getRequestURI()));

    }

    //endregion

    //region ========== Métodos auxiliares privados ==========

    /**
     * Arma la lista de errores para el contrato {@code AccesMedError} a partir de una
     * {@code AccesMedException}: siempre una lista, con un único elemento salvo que sea una
     * {@code ValidacionException} con varios errores acumulados.
     *
     * @param accesMedException {@code AccesMedException} excepción de negocio
     * @return {@code List<String>} lista de errores lista para el contrato del front
     */
    private List<String> errorsOf(AccesMedException accesMedException) {

        if (accesMedException instanceof ValidacionException validacionException) {
            return validacionException.getErrores();
        }

        return List.of(accesMedException.getMessage());

    }

    /**
     * Arma la respuesta 422 de validación con el mensaje general y la lista de errores.
     *
     * @param errores {@code List<String>} errores legibles por campo
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} la respuesta de validación
     */
    private ResponseEntity<AccesMedError> validationResponse(List<String> errores, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(AccesMedError.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "VALIDACION",
                "Hay datos incompletos o incorrectos. Revisá los campos indicados.", errores, request.getRequestURI()));

    }

    /**
     * Arma el texto de un error de campo. Los mensajes por defecto de Bean Validation
     * ("no debe ser nulo", "debe ser mayor que 0") empiezan en minúscula y no nombran el
     * campo, así que se les antepone su nombre legible. Los mensajes propios del proyecto
     * ya son oraciones completas (empiezan en mayúscula) y se devuelven tal cual.
     *
     * @param campo {@code String} ruta técnica del campo (ej. {@code prestaciones[0].precioParticular})
     * @param mensaje {@code String} mensaje de la constraint
     * @return {@code String} texto legible del error
     */
    private String mensajeDeCampo(String campo, String mensaje) {

        if (mensaje == null || mensaje.isBlank()) {
            return "El campo '" + nombreLegible(campo) + "' no es válido.";
        }

        if (Character.isUpperCase(mensaje.charAt(0))) {
            return mensaje;
        }

        boolean esObligatorio = MENSAJES_CAMPO_OBLIGATORIO.contains(mensaje);

        return capitalizar(nombreLegible(campo)) + " " + (esObligatorio ? "es obligatorio" : mensaje) + ".";

    }

    /**
     * Obtiene el nombre del último nodo de la ruta de una violación de constraint (el campo
     * o parámetro concreto), descartando el prefijo técnico de método y argumento.
     *
     * @param violation {@code ConstraintViolation<?>} violación de constraint
     * @return {@code String} nombre del último nodo de la ruta
     */
    private String ultimoNodo(ConstraintViolation<?> violation) {

        String ultimoNodo = "";
        for (Path.Node nodo : violation.getPropertyPath()) {
            ultimoNodo = nodo.getName() != null ? nodo.getName() : ultimoNodo;
        }
        return ultimoNodo;

    }

    /**
     * Convierte una ruta técnica de campo en un texto legible: {@code fechaInicioVigencia}
     * pasa a "fecha inicio vigencia"; {@code prestaciones[0].precioParticular} pasa a
     * "prestaciones (ítem 1), precio particular".
     *
     * @param campo {@code String} ruta técnica del campo
     * @return {@code String} nombre legible del campo
     */
    private String nombreLegible(String campo) {

        if (campo == null || campo.isBlank()) {
            return "dato";
        }

        List<String> partes = new ArrayList<>();
        for (String segmento : campo.split("\\.")) {
            int corchete = segmento.indexOf('[');
            String nombre = corchete >= 0 ? segmento.substring(0, corchete) : segmento;
            String legible = nombre.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
            if (corchete >= 0) {
                String indice = segmento.substring(corchete + 1, segmento.indexOf(']', corchete));
                legible = legible + (indice.matches("\\d+") ? " (ítem " + (Integer.parseInt(indice) + 1) + ")" : "");
            }
            partes.add(legible.trim());
        }
        return String.join(", ", partes);

    }

    /**
     * Pone en mayúscula la primera letra del texto.
     *
     * @param texto {@code String} texto a capitalizar
     * @return {@code String} el texto con la primera letra en mayúscula
     */
    private String capitalizar(String texto) {

        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);

    }

    /**
     * Explica en español por qué no se pudo leer el cuerpo de la request: qué campo tiene un
     * valor con formato inválido y qué se esperaba, o que el JSON está mal formado.
     *
     * @param httpMessageNotReadableException {@code HttpMessageNotReadableException} excepción de lectura
     * @return {@code String} mensaje para el usuario, sin detalle técnico del deserializador
     */
    private String mensajeDeCuerpoIlegible(HttpMessageNotReadableException httpMessageNotReadableException) {

        Throwable causa = httpMessageNotReadableException.getCause();

        if (causa instanceof MismatchedInputException mismatchedInputException && !mismatchedInputException.getPath().isEmpty()) {
            String campo = rutaDeCampo(mismatchedInputException);
            return "El valor del campo '" + nombreLegible(campo) + "' no es válido. Se esperaba "
                    + descripcionDeTipo(mismatchedInputException.getTargetType()) + ".";
        }

        if (causa instanceof JacksonException) {
            return "Los datos enviados no tienen un formato válido. Revisá que estén completos y bien escritos.";
        }

        return "No se recibieron datos válidos. Revisá que la solicitud tenga su contenido completo.";

    }

    /**
     * Reconstruye la ruta del campo (ej. {@code prestaciones[0].fechaInicioVigencia}) a partir del
     * camino que registra Jackson al fallar.
     *
     * @param jacksonException {@code JacksonException} excepción con el camino del campo
     * @return {@code String} ruta técnica del campo
     */
    private String rutaDeCampo(JacksonException jacksonException) {

        StringBuilder ruta = new StringBuilder();
        for (JacksonException.Reference referencia : jacksonException.getPath()) {
            if (referencia.getPropertyName() != null) {
                ruta.append(ruta.isEmpty() ? "" : ".").append(referencia.getPropertyName());
            } else if (referencia.getIndex() >= 0) {
                ruta.append('[').append(referencia.getIndex()).append(']');
            }
        }
        return ruta.toString();

    }

    /**
     * Describe en español el tipo de dato que se esperaba, para orientar al usuario.
     *
     * @param tipo {@code Class<?>} tipo esperado, o {@code null} si se desconoce
     * @return {@code String} descripción del valor esperado
     */
    private String descripcionDeTipo(Class<?> tipo) {

        if (tipo == null) {
            return "un valor con el formato correcto";
        }
        if (tipo.isEnum()) {
            return "uno de estos valores: " + String.join(", ",
                    Arrays.stream(tipo.getEnumConstants()).map(Object::toString).toList());
        }
        if (tipo == UUID.class) {
            return "un identificador válido";
        }
        if (tipo == LocalDate.class) {
            return "una fecha con formato AAAA-MM-DD (por ejemplo, 2026-03-15)";
        }
        if (tipo == LocalTime.class) {
            return "una hora con formato HH:mm (por ejemplo, 09:30)";
        }
        if (tipo == ZonedDateTime.class || tipo == Instant.class) {
            return "una fecha y hora con formato AAAA-MM-DDTHH:mm:ss y zona horaria (por ejemplo, 2026-03-15T09:30:00-03:00)";
        }
        if (Number.class.isAssignableFrom(tipo) || tipo == int.class || tipo == long.class || tipo == double.class) {
            return "un número";
        }
        if (tipo == Boolean.class || tipo == boolean.class) {
            return "verdadero o falso (true o false)";
        }
        if (tipo == String.class) {
            return "un texto";
        }
        return "un valor con el formato correcto";

    }

    /**
     * Traduce el mensaje de la base de datos ante una violación de integridad a un texto
     * legible, reconociendo por su nombre las constraints conocidas del esquema.
     *
     * @param mensajeCausaRaiz {@code String} mensaje técnico de la causa raíz (Postgres)
     * @return {@code String} mensaje para el usuario, sin nombres de constraints ni tablas
     */
    private String mensajeDeIntegridad(String mensajeCausaRaiz) {

        String mensaje = mensajeCausaRaiz != null ? mensajeCausaRaiz : "";

        if (mensaje.contains("no_solapamiento")) {
            return "El período indicado se superpone con otro ya registrado. Elegí fechas que no se pisen.";
        }
        if (mensaje.contains("duplicate key") || mensaje.contains("uq_")) {
            return "Ya existe un registro con esos mismos datos.";
        }
        if (mensaje.contains("foreign key") || mensaje.contains("fk_")) {
            return "No se puede completar la operación porque el registro está relacionado con otros datos.";
        }
        return "No se pudo completar la operación porque los datos entran en conflicto con información ya registrada.";

    }

    //endregion

}
