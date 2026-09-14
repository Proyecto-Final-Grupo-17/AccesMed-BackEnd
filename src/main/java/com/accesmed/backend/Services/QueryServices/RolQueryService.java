package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Records.Rol.Response.GetRolResponse;
import com.accesmed.backend.Repositories.RolRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.RolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Rol}.
 * Expone métodos para obtener un rol por id y listar todos los roles activos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final RolRepository rolRepository;
    private final RolMapper rolMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Busca un rol activo por su identificador.
     *
     * @param id {@code UUID} identificador del rol
     * @return {@code GetRolResponse} el rol activo
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         un rol activo con ese id
     */
    public GetRolResponse findRolById(UUID id) {

        log.debug("Buscando rol por id: {}", id);

        Rol rolExistente = rolRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el rol activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ROL_NO_ENCONTRADO",
                            "No se encontró el rol solicitado.");
                });

        return rolMapper.toGetResponse(rolExistente);

    }

    /**
     * Lista todos los roles activos.
     *
     * @return {@code List<GetRolResponse>} listado de todos los roles activos
     */
    public List<GetRolResponse> findRoles() {

        log.debug("Listando todos los roles activos");

        List<Rol> rolesActivos = rolRepository.findAll().stream()
                .filter(rol -> rol.getDeletedAt() == null)
                .toList();

        return rolesActivos.stream()
                .map(rolMapper::toGetResponse)
                .toList();

    }

    //endregion

}
