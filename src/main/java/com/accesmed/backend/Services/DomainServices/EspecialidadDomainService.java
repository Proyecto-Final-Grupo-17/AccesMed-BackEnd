package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Repositories.EspecialidadRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Especialidad}.
 * Encapsula las operaciones de búsqueda.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspecialidadDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadRepository especialidadRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Busca una especialidad activa por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code Especialidad} la especialidad activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         especialidad activa con ese id
     */
    public Especialidad findEspecialidadById(UUID id) {

        log.debug("Buscando especialidad por id: {}", id);

        return especialidadRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la especialidad: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                            "No existe una especialidad activa con el id " + id);
                });

    }

    //endregion

}
