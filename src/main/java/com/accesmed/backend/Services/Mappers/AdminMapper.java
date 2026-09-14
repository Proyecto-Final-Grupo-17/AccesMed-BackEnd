package com.accesmed.backend.Services.Mappers;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Records.Admin.Request.CreateAdminRequest;
import com.accesmed.backend.Records.Admin.Request.UpdateAdminRequest;
import com.accesmed.backend.Records.Admin.Response.CreateAdminResponse;
import com.accesmed.backend.Records.Admin.Response.GetAdminResponse;
import com.accesmed.backend.Records.Admin.Response.UpdateAdminResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper para la entidad {@code Admin}. Realiza conversiones entre records de
 * request/response y la entidad JPA.
 */
@Mapper(componentModel = "spring")
public interface AdminMapper {

    //region ========== Conversiones a entidad ==========

    /**
     * Convierte un {@code CreateAdminRequest} a una entidad {@code Admin}.
     *
     * @param createAdminRequest {@code CreateAdminRequest} datos del request
     * @return {@code Admin} entidad lista para persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    Admin toEntity(CreateAdminRequest createAdminRequest);

    //endregion

    //region ========== Conversiones a Response ==========

    /**
     * Convierte una entidad {@code Admin} a {@code CreateAdminResponse}.
     *
     * @param admin {@code Admin} entidad
     * @return {@code CreateAdminResponse} response de creación
     */
    CreateAdminResponse toCreateResponse(Admin admin);

    /**
     * Convierte una entidad {@code Admin} a {@code UpdateAdminResponse}.
     *
     * @param admin {@code Admin} entidad
     * @return {@code UpdateAdminResponse} response de actualización
     */
    UpdateAdminResponse toUpdateResponse(Admin admin);

    /**
     * Convierte una entidad {@code Admin} a {@code GetAdminResponse}.
     *
     * @param admin {@code Admin} entidad
     * @return {@code GetAdminResponse} response de lectura
     */
    GetAdminResponse toGetResponse(Admin admin);

    //endregion

    //region ========== Actualizaciones ==========

    /**
     * Actualiza un admin existente con datos de {@code UpdateAdminRequest}. Solo actualiza
     * nombre y apellido. El DNI y email no se modifican.
     *
     * @param admin {@code Admin} entidad a actualizar
     * @param updateAdminRequest {@code UpdateAdminRequest} datos del request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dni", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedReason", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void update(@MappingTarget Admin admin, UpdateAdminRequest updateAdminRequest);

    //endregion

}
