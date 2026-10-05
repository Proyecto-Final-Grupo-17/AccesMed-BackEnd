package com.accesmed.backend.Controllers.Validators;

import com.accesmed.backend.Domain.ModalidadCobertura;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * Implementación de {@link CoherenciaCobertura}. Acumula un mensaje puntual por campo en
 * conflicto en vez de un único mensaje genérico, para que el front pueda mostrar el error
 * junto al campo correspondiente.
 */
public class CoherenciaCoberturaValidator implements ConstraintValidator<CoherenciaCobertura, TieneCobertura> {

    @Override
    public boolean isValid(TieneCobertura tieneCobertura, ConstraintValidatorContext context) {

        if (tieneCobertura == null || tieneCobertura.modalidadCobertura() == null) {
            //La ausencia de modalidad ya la reporta @NotNull sobre ese campo; nada que validar acá
            return true;
        }

        ModalidadCobertura modalidadCobertura = tieneCobertura.modalidadCobertura();
        BigDecimal porcentajeCobertura = tieneCobertura.porcentajeCobertura();
        BigDecimal coseguro = tieneCobertura.coseguro();

        boolean esValido = switch (modalidadCobertura) {
            case TOTAL -> porcentajeCobertura == null && coseguro == null;
            case CARGO_FIJO -> coseguro != null && porcentajeCobertura == null;
            case PORCENTUAL -> porcentajeCobertura != null && coseguro == null;
        };

        if (esValido) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        String mensaje = switch (modalidadCobertura) {
            case TOTAL -> "La modalidad TOTAL no admite porcentaje de cobertura ni coseguro.";
            case CARGO_FIJO -> "La modalidad CARGO_FIJO exige coseguro y no admite porcentaje de cobertura.";
            case PORCENTUAL -> "La modalidad PORCENTUAL exige porcentaje de cobertura y no admite coseguro.";
        };
        context.buildConstraintViolationWithTemplate(mensaje).addConstraintViolation();

        return false;

    }

}
