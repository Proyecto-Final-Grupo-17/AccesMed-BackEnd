package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Records.Usuario.Response.GetUsuarioResponse;
import com.accesmed.backend.Records.Usuario.Response.ListUsuarioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper para la entidad {@code Usuario}. El nombre/apellido de la persona vinculada
 * (médico o admin) y los roles vigentes se resuelven fuera del mapeo directo de campos,
 * porque dependen de a cuál de las dos relaciones apunta el usuario y de una consulta
 * aparte respectivamente — llegan ya calculados desde el {@code QueryService}.
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    /**
     * Convierte una entidad {@code Usuario} a {@code GetUsuarioResponse}.
     *
     * @param usuario {@code Usuario} entidad
     * @param nombre {@code String} nombre de la persona vinculada, ya resuelto
     * @param apellido {@code String} apellido de la persona vinculada, ya resuelto
     * @param roles {@code List<String>} nombres de los roles vigentes, ya resueltos
     * @return {@code GetUsuarioResponse} respuesta de obtención
     */
    @Mapping(target = "medicoId", expression = "java(usuario.getMedico() != null ? usuario.getMedico().getId() : null)")
    @Mapping(target = "adminId", expression = "java(usuario.getAdmin() != null ? usuario.getAdmin().getId() : null)")
    @Mapping(target = "activo", expression = "java(usuario.getDeletedAt() == null)")
    GetUsuarioResponse toGetResponse(Usuario usuario, String nombre, String apellido, List<String> roles);

    /**
     * Convierte una entidad {@code Usuario} a {@code ListUsuarioResponse}.
     *
     * @param usuario {@code Usuario} entidad
     * @param nombre {@code String} nombre de la persona vinculada, ya resuelto
     * @param apellido {@code String} apellido de la persona vinculada, ya resuelto
     * @param roles {@code List<String>} nombres de los roles vigentes, ya resueltos
     * @return {@code ListUsuarioResponse} respuesta de listado
     */
    @Mapping(target = "medicoId", expression = "java(usuario.getMedico() != null ? usuario.getMedico().getId() : null)")
    @Mapping(target = "adminId", expression = "java(usuario.getAdmin() != null ? usuario.getAdmin().getId() : null)")
    @Mapping(target = "activo", expression = "java(usuario.getDeletedAt() == null)")
    ListUsuarioResponse toListResponse(Usuario usuario, String nombre, String apellido, List<String> roles);

}
