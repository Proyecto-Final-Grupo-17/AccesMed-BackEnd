package com.accesmed.backend.Controllers.Errors;

import com.accesmed.backend.Services.Errors.AccesMedException;
import com.accesmed.backend.Services.Errors.ValidacionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Manejador global de excepciones. Traduce cualquier error de la aplicación al contrato
 * único {@code AccesMedError} que ve el front.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //region ========== Métodos ==========

    /**
     * Traduce cualquier {@code AccesMedException} (y sus subclases) a {@code AccesMedError},
     * usando su código y status HTTP propios. No la loguea: ya se logueó en el Service que la
     * lanzó, justo antes del {@code throw}.
     *
     * @param accesMedException {@code AccesMedException} excepción de negocio lanzada por un Service
     * @param request {@code HttpServletRequest} request HTTP que disparó el error
     * @return {@code ResponseEntity<AccesMedError>} el error traducido al contrato del front
     */
    @ExceptionHandler(AccesMedException.class)
    public ResponseEntity<AccesMedError> handleAccesMedException(AccesMedException accesMedException,
                                                                   HttpServletRequest request) {

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
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        log.warn("Validación fallida en {}: {}", request.getRequestURI(), errores);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(AccesMedError.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "VALIDACION",
                "La solicitud tiene errores de validación.", errores, request.getRequestURI()));

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
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();

        log.warn("Validación fallida en {}: {}", request.getRequestURI(), errores);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(AccesMedError.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "VALIDACION",
                "La solicitud tiene errores de validación.", errores, request.getRequestURI()));

    }

    /**
     * Traduce cualquier excepción no controlada a {@code AccesMedError} con un mensaje
     * genérico, sin filtrar detalles internos al front.
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
                "Ocurrió un error inesperado. Contactá a soporte si el problema persiste.",
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

    //endregion

}
