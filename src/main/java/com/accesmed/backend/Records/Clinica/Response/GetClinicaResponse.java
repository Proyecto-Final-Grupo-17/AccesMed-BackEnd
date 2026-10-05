package com.accesmed.backend.Records.Clinica.Response;

import java.time.LocalTime;

/**
 * Record de respuesta con los datos y parámetros de configuración de la clínica.
 */
public record GetClinicaResponse(

        /**
         * Nombre de la clínica ({@code String}).
         */
        String nombre,

        /**
         * Descripción de la clínica ({@code String}).
         */
        String descripcion,

        /**
         * Ubicación física de la clínica ({@code String}).
         */
        String ubicacion,

        /**
         * Email de contacto de la clínica ({@code String}).
         */
        String email,

        /**
         * Teléfono de contacto de la clínica ({@code String}).
         */
        String telefono,

        /**
         * Horario de inicio de atención ({@code LocalTime}).
         */
        LocalTime horarioInicioAtencion,

        /**
         * Horario de fin de atención ({@code LocalTime}).
         */
        LocalTime horarioFinAtencion,

        /**
         * Zona horaria de la clínica, como identificador IANA ({@code String}).
         */
        String zonaHoraria,

        /**
         * Días máximos de anticipación con los que se puede reservar un turno
         * ({@code Integer}).
         */
        Integer diasMaximosAnticipacionReserva

) {
}
