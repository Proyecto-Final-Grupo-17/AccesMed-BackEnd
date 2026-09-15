package com.accesmed.backend.Records.Plan.Response;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.UUID;

/**
 * Record de respuesta para el listado de planes.
 */
public record ListPlanResponse(

        /**
         * Identificador único del plan ({@code UUID}).
         */
        UUID id,

        /**
         * Código del plan ({@code String}).
         */
        String codigo,

        /**
         * Nombre del plan ({@code String}).
         */
        String nombre,

        /**
         * Identificador de la obra social ({@code UUID}).
         */
        UUID obraSocialId,

        /**
         * Nombre de la obra social ({@code String}).
         */
        String obraSocialNombre,

        /**
         * Estado actual del plan ({@code EstadoPlan}).
         */
        EstadoPlan estadoActual,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
