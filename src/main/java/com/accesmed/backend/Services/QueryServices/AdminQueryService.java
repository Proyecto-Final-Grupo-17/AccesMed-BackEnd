package com.accesmed.backend.Services.QueryServices;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Records.Admin.Response.GetAdminResponse;
import com.accesmed.backend.Repositories.AdminRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Mappers.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consultas de lectura para la entidad {@code Admin}.
 * Expone métodos para obtener un admin por id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {

    //region ========== Dependencias o inyecciones ==========

    private final AdminRepository adminRepository;
    private final AdminMapper adminMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Busca un admin activo por su identificador.
     *
     * @param id {@code UUID} identificador del admin
     * @return {@code GetAdminResponse} el admin activo
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         un admin activo con ese id
     */
    public GetAdminResponse findAdminById(UUID id) {

        log.debug("Buscando admin por id: {}", id);

        Admin adminExistente = adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el admin activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ADMIN_NO_ENCONTRADO",
                            "No se encontró el admin solicitado.");
                });

        return adminMapper.toGetResponse(adminExistente);

    }

    //endregion

}
