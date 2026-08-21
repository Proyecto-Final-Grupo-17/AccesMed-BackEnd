package com.accesmed.backend.Records.MedicoPrestacion.Request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record para desasignar una prestación de un médico: cierra el período de vigencia de la
 * asignación, no la borra. {@code fechaFinVigencia} admite una fecha futura para programar
 * el corte.
 */
public record UnassignMedicoPrestacionRequest(

        /**
         * Identificador de la asignación a cerrar ({@code UUID}).
         */
        @NotNull(message = "El id de la asignación es obligatorio.")
        UUID id,

        /**
         * Fecha de fin de vigencia de la asignación ({@code LocalDate}).
         * {@code null} u ausente equivale a "hoy".
         */
        LocalDate fechaFinVigencia

) {
}
