package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Especialidad;
import com.accesmed.backend.Domain.Especialidad_;
import com.accesmed.backend.Repositories.EspecialidadRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import com.accesmed.backend.Services.Utils.CoincidenciaBusqueda;
import com.accesmed.backend.Services.Utils.ResultadoBusqueda;
import com.accesmed.backend.Services.Utils.EspecificacionBusquedaPalabras;
import com.accesmed.backend.Services.Utils.NormalizadorBusqueda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

/**
 * Lógica de dominio y persistencia para la entidad {@code Especialidad}.
 * Encapsula guardar, buscar, validaciones de unicidad y la baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspecialidadDomainService {

    //region ========== Constantes ==========

    /**
     * Máximo de resultados del fallback por similitud de trigramas.
     */
    private static final int LIMITE_SIMILITUD = 5;

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final EspecialidadRepository especialidadRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda una especialidad en la base de datos.
     *
     * @param especialidad {@code Especialidad} entidad a persistir
     * @return {@code Especialidad} la especialidad guardada
     */
    public Especialidad saveEspecialidad(Especialidad especialidad) {

        log.debug("Guardando especialidad: código={}", especialidad.getCodigo());

        return especialidadRepository.save(especialidad);

    }

    /**
     * Busca una especialidad activa por su identificador.
     *
     * @param id {@code UUID} identificador de la especialidad
     * @return {@code Especialidad} la especialidad activa correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe una
     *         especialidad activa con ese id
     */
    public Especialidad findEspecialidadActivaById(UUID id) {

        log.debug("Buscando especialidad activa por id: {}", id);

        return especialidadRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró la especialidad activa: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                            "No se encontró la especialidad solicitada. Es posible que haya sido dada de baja.");
                });

    }

    /**
     * Valida que el código de especialidad sea único entre las especialidades activas.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una especialidad
     *         activa con ese código
     */
    public void validateCodigoEspecialidadIsUnique(String codigo) {

        if (especialidadRepository.existsByCodigoIgnoreCaseAndDeletedAtIsNull(codigo)) {
            log.warn("No se pudo crear la especialidad: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CODIGO_DUPLICADO",
                    "Ya existe una especialidad con el código " + codigo + ".");
        }

    }

    /**
     * Valida que el código de especialidad sea único entre las activas, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param codigo {@code String} código a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra especialidad
     *         activa con ese código
     */
    public void validateCodigoEspecialidadIsUnique(String codigo, UUID idExcluido) {

        if (especialidadRepository.existsByCodigoIgnoreCaseAndDeletedAtIsNullAndIdNot(codigo, idExcluido)) {
            log.warn("No se pudo actualizar la especialidad: código {} ya existe en otra especialidad", codigo);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CODIGO_DUPLICADO",
                    "Ya existe otra especialidad con el código " + codigo + ".");
        }

    }

    /**
     * Valida que el nombre de especialidad sea único entre las especialidades activas.
     *
     * @param nombre {@code String} nombre a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una especialidad
     *         activa con ese nombre
     */
    public void validateNombreEspecialidadIsUnique(String nombre) {

        if (especialidadRepository.existsByNombreIgnoreCaseAndDeletedAtIsNull(nombre)) {
            log.warn("No se pudo crear la especialidad: nombre {} ya existe", nombre);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_NOMBRE_DUPLICADO",
                    "Ya existe una especialidad con el nombre " + nombre + ".");
        }

    }

    /**
     * Valida que el nombre de especialidad sea único entre las activas, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param nombre {@code String} nombre a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otra especialidad
     *         activa con ese nombre
     */
    public void validateNombreEspecialidadIsUnique(String nombre, UUID idExcluido) {

        if (especialidadRepository.existsByNombreIgnoreCaseAndDeletedAtIsNullAndIdNot(nombre, idExcluido)) {
            log.warn("No se pudo actualizar la especialidad: nombre {} ya existe en otra especialidad", nombre);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_NOMBRE_DUPLICADO",
                    "Ya existe otra especialidad con el nombre " + nombre + ".");
        }

    }

    /**
     * Realiza la baja lógica de una especialidad.
     *
     * @param especialidad {@code Especialidad} especialidad a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteEspecialidad(Especialidad especialidad, String motivo) {

        log.debug("Dando de baja especialidad: código={}, motivo={}", especialidad.getCodigo(), motivo);

        especialidad.setDeletedAt(Instant.now());
        especialidad.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveEspecialidad(especialidad);

    }

    /**
     * Busca una especialidad activa por su nombre, sin distinguir mayúsculas.
     *
     * @param nombre {@code String} nombre de la especialidad
     * @return {@code Especialidad} la especialidad activa con ese nombre
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe
     *         una especialidad activa con ese nombre
     */
    public Especialidad findEspecialidadActivaByNombre(String nombre) {

        log.debug("Buscando especialidad activa por nombre: {}", nombre);

        return especialidadRepository.findByNombreIgnoreCaseAndDeletedAtIsNull(nombre)
                .orElseThrow(() -> {
                    log.warn("No se encontró la especialidad activa: nombre={}", nombre);
                    return new RecursoNoEncontradoException(getClass(), "ESPECIALIDAD_NO_ENCONTRADA",
                            "No encontramos la especialidad \"" + nombre + "\".");
                });

    }

    /**
     * Lista las especialidades activas ordenadas por nombre.
     *
     * @return {@code List<Especialidad>} las especialidades activas
     */
    public List<Especialidad> findEspecialidadesActivas() {

        log.debug("Listando especialidades activas");

        return especialidadRepository.findByDeletedAtIsNullOrderByNombreAsc();

    }

    /**
     * Busca especialidades activas por parecido con el nombre (canal chatbot): primero por palabras
     * (cada palabra contenida en el nombre, sin distinguir mayúsculas ni tildes) y, si no hay
     * coincidencias, por similitud de trigramas para tolerar errores de tipeo.
     *
     * @param nombre {@code String} texto libre que escribió el paciente
     * @param umbralSimilitud {@code double} similitud mínima de trigramas para el fallback (0 a 1)
     * @return {@code ResultadoBusqueda<Especialidad>} las coincidencias (vacía si no hay ninguna) y si fueron exactas o aproximadas
     */
    public ResultadoBusqueda<Especialidad> searchEspecialidadesActivasByNombre(String nombre, double umbralSimilitud) {

        log.debug("Buscando especialidades activas por parecido: {}", nombre);

        List<String> palabrasBusqueda = NormalizadorBusqueda.findPalabrasBusqueda(nombre);
        if (palabrasBusqueda.isEmpty()) {
            return ResultadoBusqueda.vacio();
        }

        //Coincidencia por palabras: cada una contenida en el nombre
        Specification<Especialidad> specificationBusqueda = EspecificacionBusquedaPalabras
                .<Especialidad>buildSpecificationPalabras(palabrasBusqueda, List.of(Especialidad_.nombre))
                .and((root, query, cb) -> cb.isNull(root.get(Especialidad_.deletedAt)));
        List<Especialidad> especialidadesEncontradas = especialidadRepository.findAll(specificationBusqueda,
                Sort.by(Especialidad_.NOMBRE));

        //Sin coincidencias exactas por palabra: probar por similitud (errores de tipeo), avisando que es aproximada
        ResultadoBusqueda<Especialidad> resultadoBusqueda = new ResultadoBusqueda<>(especialidadesEncontradas, CoincidenciaBusqueda.EXACTA);
        if (especialidadesEncontradas.isEmpty()) {
            resultadoBusqueda = new ResultadoBusqueda<>(especialidadRepository.findActivasBySimilitudNombre(
                    String.join(" ", palabrasBusqueda), umbralSimilitud, LIMITE_SIMILITUD), CoincidenciaBusqueda.APROXIMADA);
        }

        return resultadoBusqueda;

    }

    //endregion

}
