package com.accesmed.backend.Records.Auditoria;

import java.time.Instant;
import java.util.UUID;

/**
 * Record compartido que embebe los datos de auditoría de una entidad en sus Response de
 * lectura. Solo se completa cuando quien consulta tiene {@code AUDITORIA_CONSULTAR}; en
 * caso contrario el campo {@code auditoria} del Response queda en {@code null} (ver
 * {@code Docs/ARQUITECTURA.md}). Todos los campos son nullable: {@code deletedAt}/
 * {@code deletedBy}/{@code deletedReason} solo se completan en las entidades con soft
 * delete propio (no es parte de {@code Auditable}).
 */
public record AuditoriaResponse(

        /**
         * Fecha y hora de creación en UTC ({@code Instant}).
         */
        Instant createdAt,

        /**
         * Usuario que creó el registro ({@code String}).
         */
        String createdBy,

        /**
         * Fecha y hora de la última modificación en UTC ({@code Instant}).
         */
        Instant updatedAt,

        /**
         * Usuario que hizo la última modificación ({@code String}).
         */
        String updatedBy,

        /**
         * Fecha y hora de baja lógica en UTC ({@code Instant}), o {@code null} si la
         * entidad no tiene soft delete o está activa.
         */
        Instant deletedAt,

        /**
         * Identificador del usuario que dio de baja el registro ({@code UUID}), o
         * {@code null} si la entidad no tiene soft delete o está activa.
         */
        UUID deletedBy,

        /**
         * Motivo de la baja lógica ({@code String}), o {@code null} si la entidad no tiene
         * soft delete o está activa.
         */
        String deletedReason

) {
}
