package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Admin;
import com.accesmed.backend.Repositories.AdminRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Admin}.
 * Encapsula guardar, buscar, validaciones de unicidad y la baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final AdminRepository adminRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un admin en la base de datos.
     *
     * @param admin {@code Admin} entidad a persistir
     * @return {@code Admin} el admin guardado
     */
    public Admin saveAdmin(Admin admin) {

        log.debug("Guardando admin: nombre={}, apellido={}", admin.getNombre(), admin.getApellido());

        return adminRepository.save(admin);

    }

    /**
     * Busca un admin activo por su identificador.
     *
     * @param id {@code UUID} identificador del admin
     * @return {@code Admin} el admin activo correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         admin activo con ese id
     */
    public Admin findAdminActivoById(UUID id) {

        log.debug("Buscando admin activo por id: {}", id);

        return adminRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el admin activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ADMIN_NO_ENCONTRADO",
                            "No existe un admin activo con el id " + id);
                });

    }

    /**
     * Valida que el DNI del admin sea único entre los admins activos.
     *
     * @param dni {@code String} DNI a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un admin
     *         activo con ese DNI
     */
    public void validateDniIsUnique(String dni) {

        if (adminRepository.existsByDniAndDeletedAtIsNull(dni)) {
            log.warn("No se pudo crear el admin: DNI {} ya existe", dni);
            throw new ReglaNegocioException(getClass(), "ADMIN_DNI_DUPLICADO",
                    "Ya existe un admin activo con el DNI " + dni);
        }

    }

    /**
     * Valida que el email del admin sea único entre los admins activos.
     *
     * @param email {@code String} email a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un admin
     *         activo con ese email
     */
    public void validateEmailIsUnique(String email) {

        if (adminRepository.existsByEmailAndDeletedAtIsNull(email)) {
            log.warn("No se pudo crear el admin: email {} ya existe", email);
            throw new ReglaNegocioException(getClass(), "ADMIN_EMAIL_DUPLICADO",
                    "Ya existe un admin activo con el email " + email);
        }

    }

    /**
     * Realiza la baja lógica de un admin.
     *
     * @param admin {@code Admin} admin a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteAdmin(Admin admin, String motivo) {

        log.debug("Dando de baja admin: nombre={}, apellido={}, motivo={}", admin.getNombre(), admin.getApellido(), motivo);

        admin.setDeletedAt(Instant.now());
        admin.setDeletedReason(motivo);

        saveAdmin(admin);

    }

    //endregion

}
