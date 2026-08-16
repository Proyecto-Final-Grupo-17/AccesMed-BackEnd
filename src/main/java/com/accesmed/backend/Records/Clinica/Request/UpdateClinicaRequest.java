package com.accesmed.backend.Records.Clinica.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Record para actualizar los datos y parámetros de la clínica. Al ser una instancia
 * única, no lleva {@code id}. Un campo en {@code null} o ausente significa "no lo
 * toques".
 */
public record UpdateClinicaRequest(

        /**
         * Nombre de la clínica ({@code String}). Máximo 150 caracteres.
         * {@code null} deja el nombre sin tocar.
         */
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /**
         * Descripción de la clínica ({@code String}). Máximo 1000 caracteres.
         * {@code null} deja la descripción sin tocar.
         */
        @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres.")
        String descripcion,

        /**
         * Ubicación física de la clínica ({@code String}). Máximo 250 caracteres.
         * {@code null} deja la ubicación sin tocar.
         */
        @Size(max = 250, message = "La ubicación no puede exceder 250 caracteres.")
        String ubicacion,

        /**
         * Email de contacto de la clínica ({@code String}). Máximo 150 caracteres.
         * {@code null} deja el email sin tocar.
         */
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres.")
        String email,

        /**
         * Teléfono de contacto de la clínica ({@code String}). Máximo 30 caracteres.
         * {@code null} deja el teléfono sin tocar.
         */
        @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres.")
        String telefono,

        /**
         * Horario de inicio de atención ({@code LocalTime}). Debe ser anterior al horario
         * de fin de atención (efectivo, considerando el otro campo si no viene en el mismo
         * request). {@code null} deja el horario de inicio sin tocar.
         */
        LocalTime horarioInicioAtencion,

        /**
         * Horario de fin de atención ({@code LocalTime}). Debe ser posterior al horario de
         * inicio de atención (efectivo). {@code null} deja el horario de fin sin tocar.
         */
        LocalTime horarioFinAtencion,

        /**
         * Días máximos de anticipación con los que se puede reservar un turno
         * ({@code Integer}). Debe ser mayor o igual a 1. {@code null} deja el valor sin
         * tocar.
         */
        @Min(value = 1, message = "Los días máximos de anticipación de reserva deben ser al menos 1.")
        Integer diasMaximosAnticipacionReserva

) {
}
