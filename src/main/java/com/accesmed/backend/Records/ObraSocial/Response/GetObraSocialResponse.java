package com.accesmed.backend.Records.ObraSocial.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.List;
import java.util.UUID;

/**
 * Record de respuesta para la obtención de una obra social por identificador, con sus planes.
 */
public record GetObraSocialResponse(

        /**
         * Identificador único de la obra social ({@code UUID}).
         */
        UUID id,

        /**
         * Código único de la obra social ({@code String}).
         */
        String codigo,

        /**
         * Nombre de la obra social ({@code String}).
         */
        String nombre,

        /**
         * Razón social de la obra social ({@code String}).
         */
        String razonSocial,

        /**
         * Planes de la obra social ({@code List<GetPlanAnidadoResponse>}).
         */
        List<GetPlanAnidadoResponse> planes,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
