package com.accesmed.backend.Security.Records.Auth.Response;

import java.util.List;
import java.util.UUID;

/**
 * Response con los datos del usuario autenticado: perfil y roles vigentes con sus
 * permisos. {@code medicoId}/{@code adminId} son mutuamente excluyentes: exactamente
 * uno de los dos es no nulo.
 *
 * @param id {@code UUID} identificador del usuario
 * @param mail {@code String} correo electrónico del usuario
 * @param nombre {@code String} nombre de la persona vinculada (médico o admin)
 * @param apellido {@code String} apellido de la persona vinculada (médico o admin)
 * @param medicoId {@code UUID} identificador del médico vinculado, o {@code null} si el
 *        usuario no es médico
 * @param adminId {@code UUID} identificador del admin vinculado, o {@code null} si el
 *        usuario no es admin
 * @param roles {@code List<MeRolResponse>} roles vigentes del usuario, con sus permisos
 */
public record MeResponse(
        UUID id,
        String mail,
        String nombre,
        String apellido,
        UUID medicoId,
        UUID adminId,
        List<MeRolResponse> roles
) {
}
