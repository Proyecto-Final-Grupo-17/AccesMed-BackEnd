package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Rol;
import com.accesmed.backend.Repositories.RolRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Rol}.
 * Encapsula guardar, buscar, validaciones de unicidad y reglas de negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final RolRepository rolRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un rol en la base de datos.
     *
     * @param rol {@code Rol} entidad a persistir
     * @return {@code Rol} el rol guardado
     */
    public Rol saveRol(Rol rol) {

        log.debug("Guardando rol: nombre={}", rol.getNombre());

        return rolRepository.save(rol);

    }

    /**
     * Busca un rol activo por su identificador.
     *
     * @param id {@code UUID} identificador del rol
     * @return {@code Rol} el rol activo correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         rol activo con ese id
     */
    public Rol findRolActivoById(UUID id) {

        log.debug("Buscando rol activo por id: {}", id);

        return rolRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el rol activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ROL_NO_ENCONTRADO",
                            "No se encontró el rol solicitado.");
                });

    }

    /**
     * Busca un rol activo por su nombre.
     *
     * @param nombre {@code String} nombre del rol
     * @return {@code Rol} el rol activo correspondiente al nombre
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         rol activo con ese nombre
     */
    public Rol findRolActivoByNombre(String nombre) {

        log.debug("Buscando rol activo por nombre: {}", nombre);

        return rolRepository.findByNombreAndDeletedAtIsNull(nombre)
                .orElseThrow(() -> {
                    log.warn("No se encontró el rol activo: nombre={}", nombre);
                    return new RecursoNoEncontradoException(getClass(), "ROL_NO_ENCONTRADO",
                            "No se encontró el rol de sistema '" + nombre + "'.");
                });

    }

    /**
     * Valida que el nombre del rol sea único entre los roles activos.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un rol
     *         activo con ese nombre
     */
    public void validateNombreRolIsUnique(String nombre) {

        if (rolRepository.existsByNombreAndDeletedAtIsNull(nombre)) {
            log.warn("No se pudo crear el rol: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "ROL_NOMBRE_DUPLICADO",
                    "Ya existe un rol activo con el nombre " + nombre);
        }

    }

    /**
     * Valida que un rol no sea un rol de sistema. Los roles de sistema no pueden
     * modificarse ni darse de baja.
     *
     * @param rol {@code Rol} rol a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el rol es un rol
     *         de sistema
     */
    public void validateRolEditable(Rol rol) {

        if (Boolean.TRUE.equals(rol.getEsSistema())) {
            log.warn("No se puede modificar el rol de sistema: {}", rol.getNombre());
            throw new ReglaNegocioException(getClass(), "ROL_SISTEMA_NO_EDITABLE",
                    "El rol '" + rol.getNombre() + "' es un rol de sistema y no puede modificarse ni darse de baja.");
        }

    }

    //endregion

}
