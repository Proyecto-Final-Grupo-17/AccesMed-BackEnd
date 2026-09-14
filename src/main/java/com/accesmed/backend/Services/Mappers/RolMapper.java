package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Records.Rol.Request.CreateRolRequest;
import com.accesmed.backend.Records.Rol.Request.UpdateRolRequest;
import com.accesmed.backend.Records.Rol.Response.CreateRolResponse;
import com.accesmed.backend.Records.Rol.Response.GetRolResponse;
import com.accesmed.backend.Records.Rol.Response.UpdateRolResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper para la entidad {@code Rol}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 *
 * Nota: los roles de sistema ({@code esSistema = true}) se crean solo desde
 * la base de datos o migraciones Liquibase, nunca desde la aplicación.
 */
@Mapper(componentModel = "spring")
public interface RolMapper {

    //region ========== Conversiones a entidad ==========

    /**
     * Convierte un {@code CreateRolRequest} a una entidad {@code Rol}.
     *
     * @param createRolRequest {@code CreateRolRequest} datos del request
     * @return {@code Rol} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "esSistema", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    Rol toEntity(CreateRolRequest createRolRequest);

    //endregion

    //region ========== Conversiones a Response ==========

    /**
     * Convierte una entidad {@code Rol} a {@code CreateRolResponse}.
     *
     * @param rol {@code Rol} entidad
     * @return {@code CreateRolResponse} response de creación
     */
    CreateRolResponse toCreateResponse(Rol rol);

    /**
     * Convierte una entidad {@code Rol} a {@code UpdateRolResponse}.
     *
     * @param rol {@code Rol} entidad
     * @return {@code UpdateRolResponse} response de actualización
     */
    UpdateRolResponse toUpdateResponse(Rol rol);

    /**
     * Convierte una entidad {@code Rol} a {@code GetRolResponse}.
     *
     * @param rol {@code Rol} entidad
     * @return {@code GetRolResponse} response de lectura
     */
    GetRolResponse toGetResponse(Rol rol);

    //endregion

    //region ========== Actualizaciones ==========

    /**
     * Actualiza un rol existente con datos de {@code UpdateRolRequest}. Los campos
     * {@code id} y {@code esSistema} no se modifican.
     *
     * @param rol {@code Rol} entidad a actualizar
     * @param updateRolRequest {@code UpdateRolRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "esSistema", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void update(@MappingTarget Rol rol, UpdateRolRequest updateRolRequest);

    //endregion

}
