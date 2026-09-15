package com.accesmed.backend.Records.Usuario.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Record de respuesta para el listado de usuarios. Incluye activos e inactivos según el
 * {@code estado} pedido en el criteria.
 */
public record ListUsuarioResponse(

        /**
         * Identificador único del usuario ({@code UUID}).
         */
        UUID id,

        /**
         * Mail de login del usuario ({@code String}).
         */
        String mail,

        /**
         * Identificador del médico vinculado ({@code UUID}), o {@code null} si está
         * vinculado a un admin.
         */
        UUID medicoId,

        /**
         * Identificador del admin vinculado ({@code UUID}), o {@code null} si está
         * vinculado a un médico.
         */
        UUID adminId,

        /**
         * Nombre de la persona vinculada (médico o admin) ({@code String}).
         */
        String nombre,

        /**
         * Apellido de la persona vinculada (médico o admin) ({@code String}).
         */
        String apellido,

        /**
         * Nombres de los roles vigentes del usuario ({@code List<String>}).
         */
        List<String> roles,

        /**
         * {@code true} si el usuario está activo (no dado de baja).
         */
        boolean activo,

        /**
         * Momento de la baja lógica ({@code Instant}), o {@code null} si está activo.
         */
        Instant deletedAt

) {
}
