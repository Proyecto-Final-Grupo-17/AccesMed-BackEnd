package com.accesmed.backend.Records.Admin.Response;

import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.UUID;

/**
 * Response de lectura de un admin.
 *
 * @param id {@code UUID} identificador del admin
 * @param nombre {@code String} nombre del admin
 * @param apellido {@code String} apellido del admin
 * @param dni {@code String} DNI del admin
 * @param email {@code String} email del admin
 * @param auditoria {@code AuditoriaResponse} datos de auditoría, solo poblado si quien
 *        consulta tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario
 */
public record GetAdminResponse(
        UUID id,
        String nombre,
        String apellido,
        String dni,
        String email,
        AuditoriaResponse auditoria
) {
}
