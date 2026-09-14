package com.accesmed.backend.Records.Plan.Response;

import com.accesmed.backend.Domain.EstadoPlan;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.List;
import java.util.UUID;

/**
 * Record de respuesta para la obtención de un plan (usado también al agregarlo y al
 * actualizarlo).
 */
public record GetPlanResponse(

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
         * Prestaciones cubiertas por el plan ({@code List<GetCoberturaAnidadaResponse>}).
         */
        List<GetCoberturaAnidadaResponse> coberturas,

        /**
         * Datos de auditoría ({@code AuditoriaResponse}), solo poblado si quien consulta
         * tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario.
         */
        AuditoriaResponse auditoria

) {
}
