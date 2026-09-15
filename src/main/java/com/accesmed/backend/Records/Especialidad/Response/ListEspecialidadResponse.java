package com.accesmed.backend.Records.Especialidad.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.UUID;

/**
 * Record de respuesta para el listado de especialidades.
 */
public record ListEspecialidadResponse(

        /**
         * Identificador único de la especialidad ({@code UUID}).
         */
        UUID id,

        /**
         * Código único de la especialidad ({@code String}).
         */
        String codigo,

        /**
         * Nombre de la especialidad ({@code String}).
         */
        String nombre,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
