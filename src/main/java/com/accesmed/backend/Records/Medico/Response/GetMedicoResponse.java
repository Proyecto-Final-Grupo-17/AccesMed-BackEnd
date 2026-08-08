package com.accesmed.backend.Records.Medico.Response;

import java.util.List;
import java.util.UUID;

/**
 * Record de respuesta para la obtención de un médico por identificador, con sus
 * prestaciones asignadas.
 */
public record GetMedicoResponse(

        /**
         * Identificador único del médico ({@code UUID}).
         */
        UUID id,

        /**
         * Matrícula del médico ({@code String}).
         */
        String matricula,

        /**
         * DNI del médico ({@code String}).
         */
        String dni,

        /**
         * Nombre del médico ({@code String}).
         */
        String nombre,

        /**
         * Apellido del médico ({@code String}).
         */
        String apellido,

        /**
         * Email del médico ({@code String}).
         */
        String email,

        /**
         * Número de teléfono del médico ({@code String}).
         */
        String numeroTelefono,

        /**
         * Identificador de la especialidad del médico ({@code UUID}).
         */
        UUID especialidadId,

        /**
         * Prestaciones que atiende el médico ({@code List<GetPrestacionAnidadaResponse>}).
         */
        List<GetPrestacionAnidadaResponse> prestaciones

) {
}
