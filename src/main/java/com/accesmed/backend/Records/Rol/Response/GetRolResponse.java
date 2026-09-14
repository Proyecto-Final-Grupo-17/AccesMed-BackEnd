package com.accesmed.backend.Records.Rol.Response;

import com.accesmed.backend.Domain.Permiso;
import com.accesmed.backend.Records.Auditoria.AuditoriaResponse;

import java.util.Set;
import java.util.UUID;

/**
 * Response de lectura de un rol. Incluye indicador de rol de sistema.
 *
 * @param id {@code UUID} identificador del rol
 * @param nombre {@code String} nombre del rol
 * @param esSistema {@code Boolean} indicador de si es un rol de sistema (no editable)
 * @param permisos {@code Set<Permiso>} permisos del rol
 * @param auditoria {@code AuditoriaResponse} datos de auditoría, solo poblado si quien
 *        consulta tiene {@code AUDITORIA_CONSULTAR}; {@code null} en caso contrario
 */
public record GetRolResponse(
        UUID id,
        String nombre,
        Boolean esSistema,
        Set<Permiso> permisos,
        AuditoriaResponse auditoria
) {
}
