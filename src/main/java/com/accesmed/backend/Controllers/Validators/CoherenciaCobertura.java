package com.accesmed.backend.Controllers.Validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida, a nivel de record, la coherencia entre {@code modalidadCobertura} y sus montos
 * asociados: {@code TOTAL} exige {@code porcentajeCobertura} y {@code coseguro} ambos
 * nulos; {@code CARGO_FIJO} exige {@code coseguro} no nulo y {@code porcentajeCobertura}
 * nulo; {@code PORCENTUAL} exige {@code porcentajeCobertura} no nulo (entre 0 y 100) y
 * {@code coseguro} nulo. Espejo del {@code CHECK} de la migración de
 * {@code ObraSocialPlanPrestacion}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CoherenciaCoberturaValidator.class)
public @interface CoherenciaCobertura {

    /**
     * Mensaje de error por defecto.
     *
     * @return {@code String} mensaje de error
     */
    String message() default "La combinación de modalidad de cobertura, porcentaje y coseguro no es coherente.";

    /**
     * Grupos de validación (Bean Validation).
     *
     * @return {@code Class<?>[]} grupos de validación
     */
    Class<?>[] groups() default {};

    /**
     * Payload de validación (Bean Validation).
     *
     * @return {@code Class<? extends Payload>[]} payload de validación
     */
    Class<? extends Payload>[] payload() default {};

}
