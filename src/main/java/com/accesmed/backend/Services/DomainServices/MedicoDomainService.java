package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Medico;
import com.accesmed.backend.Domain.Medico_;
import com.accesmed.backend.Repositories.MedicoRepository;
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
import java.util.Optional;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

/**
 * Lógica de dominio y persistencia para la entidad {@code Medico}.
 * Encapsula guardar, buscar, validaciones de unicidad y la baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicoDomainService {

    //region ========== Constantes ==========

    /**
     * Máximo de resultados del fallback por similitud de trigramas.
     */
    private static final int LIMITE_SIMILITUD = 5;

    //endregion

    //region ========== Dependencias o inyecciones ==========

    private final MedicoRepository medicoRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un médico en la base de datos.
     *
     * @param medico {@code Medico} entidad a persistir
     * @return {@code Medico} el médico guardado
     */
    public Medico saveMedico(Medico medico) {

        log.debug("Guardando médico: matrícula={}", medico.getMatricula());

        return medicoRepository.save(medico);

    }

    /**
     * Busca un médico activo por su identificador.
     *
     * @param id {@code UUID} identificador del médico
     * @return {@code Medico} el médico activo correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         médico activo con ese id
     */
    public Medico findMedicoActivoById(UUID id) {

        log.debug("Buscando médico activo por id: {}", id);

        return medicoRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el médico activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_NO_ENCONTRADO",
                            "No se encontró el médico solicitado. Es posible que haya sido dado de baja.");
                });

    }

    /**
     * Valida que la matrícula del médico sea única entre los médicos activos.
     *
     * @param matricula {@code String} matrícula a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un médico
     *         activo con esa matrícula
     */
    public void validateMatriculaMedicoIsUnique(String matricula) {

        if (medicoRepository.existsByMatriculaAndDeletedAtIsNull(matricula)) {
            log.warn("No se pudo crear el médico: matrícula {} ya existe", matricula);
            throw new ReglaNegocioException(getClass(), "MEDICO_MATRICULA_DUPLICADA",
                    "Ya existe un médico con la matrícula " + matricula + ".");
        }

    }

    /**
     * Valida que la matrícula del médico sea única entre los activos, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param matricula {@code String} matrícula a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro médico
     *         activo con esa matrícula
     */
    public void validateMatriculaMedicoIsUnique(String matricula, UUID idExcluido) {

        if (medicoRepository.existsByMatriculaAndDeletedAtIsNullAndIdNot(matricula, idExcluido)) {
            log.warn("No se pudo actualizar el médico: matrícula {} ya existe en otro médico", matricula);
            throw new ReglaNegocioException(getClass(), "MEDICO_MATRICULA_DUPLICADA",
                    "Ya existe otro médico con la matrícula " + matricula + ".");
        }

    }

    /**
     * Valida que el DNI del médico sea único entre los médicos activos.
     *
     * @param dni {@code String} DNI a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un médico
     *         activo con ese DNI
     */
    public void validateDniMedicoIsUnique(String dni) {

        if (medicoRepository.existsByDniAndDeletedAtIsNull(dni)) {
            log.warn("No se pudo crear el médico: DNI {} ya existe", dni);
            throw new ReglaNegocioException(getClass(), "MEDICO_DNI_DUPLICADO",
                    "Ya existe un médico con el DNI " + dni + ".");
        }

    }

    /**
     * Valida que el DNI del médico sea único entre los activos, excluyendo un id concreto.
     * Útil para la actualización.
     *
     * @param dni {@code String} DNI a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro médico
     *         activo con ese DNI
     */
    public void validateDniMedicoIsUnique(String dni, UUID idExcluido) {

        if (medicoRepository.existsByDniAndDeletedAtIsNullAndIdNot(dni, idExcluido)) {
            log.warn("No se pudo actualizar el médico: DNI {} ya existe en otro médico", dni);
            throw new ReglaNegocioException(getClass(), "MEDICO_DNI_DUPLICADO",
                    "Ya existe otro médico con el DNI " + dni + ".");
        }

    }

    /**
     * Valida que el email del médico sea único entre los médicos activos.
     *
     * @param email {@code String} email a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un médico
     *         activo con ese email
     */
    public void validateEmailMedicoIsUnique(String email) {

        if (medicoRepository.existsByEmailAndDeletedAtIsNull(email)) {
            log.warn("No se pudo crear el médico: email {} ya existe", email);
            throw new ReglaNegocioException(getClass(), "MEDICO_EMAIL_DUPLICADO",
                    "Ya existe un médico con el email " + email + ".");
        }

    }

    /**
     * Valida que el email del médico sea único entre los activos, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param email {@code String} email a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro médico
     *         activo con ese email
     */
    public void validateEmailMedicoIsUnique(String email, UUID idExcluido) {

        if (medicoRepository.existsByEmailAndDeletedAtIsNullAndIdNot(email, idExcluido)) {
            log.warn("No se pudo actualizar el médico: email {} ya existe en otro médico", email);
            throw new ReglaNegocioException(getClass(), "MEDICO_EMAIL_DUPLICADO",
                    "Ya existe otro médico con el email " + email + ".");
        }

    }

    /**
     * Realiza la baja lógica de un médico.
     *
     * @param medico {@code Medico} médico a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteMedico(Medico medico, String motivo) {

        log.debug("Dando de baja médico: matrícula={}, motivo={}", medico.getMatricula(), motivo);

        medico.setDeletedAt(Instant.now());
        medico.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        saveMedico(medico);

    }

    /**
     * Valida que una especialidad no tenga médicos activos asociados. Precondición real
     * de la baja restrictiva de deshabilitar una especialidad.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad a validar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si hay médicos activos
     *         de esa especialidad
     */
    public void validateSinMedicosActivos(UUID especialidadId) {

        if (medicoRepository.existsByEspecialidadIdAndDeletedAtIsNull(especialidadId)) {
            log.warn("No se pudo validar sin médicos activos para la especialidad {}: tiene médicos activos", especialidadId);
            throw new ReglaNegocioException(getClass(), "ESPECIALIDAD_CON_MEDICOS_ACTIVOS",
                    "No se puede dar de baja la especialidad porque tiene médicos activos asignados.");
        }

    }

    /**
     * Busca un médico activo por su matrícula.
     *
     * @param matricula {@code String} matrícula del médico
     * @return {@code Medico} el médico activo con esa matrícula
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         médico activo con esa matrícula
     */
    public Medico findMedicoActivoByMatricula(String matricula) {

        log.debug("Buscando médico activo por matrícula: {}", matricula);

        return medicoRepository.findByMatriculaAndDeletedAtIsNull(matricula)
                .orElseThrow(() -> {
                    log.warn("No se encontró el médico activo: matrícula={}", matricula);
                    return new RecursoNoEncontradoException(getClass(), "MEDICO_NO_ENCONTRADO",
                            "No encontramos un médico con la matrícula " + matricula + ".");
                });

    }

    /**
     * Busca un médico activo por su matrícula sin lanzar excepción si no existe: lo usa la
     * verificación de respuestas del agente, donde "no existe" es un resultado y no un error.
     *
     * @param matricula {@code String} matrícula del médico
     * @return {@code Optional<Medico>} el médico activo con esa matrícula, o vacío si no hay
     */
    public Optional<Medico> findOptionalMedicoActivoByMatricula(String matricula) {

        log.debug("Buscando médico activo por matrícula (opcional): {}", matricula);

        Optional<Medico> medicoActivo = medicoRepository.findByMatriculaAndDeletedAtIsNull(matricula);
        return medicoActivo;

    }

    /**
     * Lista los médicos activos de una especialidad.
     *
     * @param especialidadId {@code UUID} identificador de la especialidad
     * @return {@code List<Medico>} los médicos activos de esa especialidad
     */
    public List<Medico> findMedicosActivosByEspecialidadId(UUID especialidadId) {

        log.debug("Listando médicos activos de la especialidad: {}", especialidadId);

        return medicoRepository.findByEspecialidadIdAndDeletedAtIsNullOrderByApellidoAscNombreAsc(especialidadId);

    }

    /**
     * Busca médicos activos por parecido con su nombre o apellido (canal chatbot): primero por
     * palabras (cada palabra contenida en el nombre o en el apellido, sin distinguir mayúsculas ni
     * tildes) y, si no hay coincidencias, por similitud de trigramas para tolerar errores de tipeo.
     *
     * @param nombre {@code String} texto libre que escribió el paciente ("Raúl", "dra gomez")
     * @param umbralSimilitud {@code double} similitud mínima de trigramas para el fallback (0 a 1)
     * @return {@code ResultadoBusqueda<Medico>} las coincidencias (vacía si no hay ninguna) y si fueron exactas o aproximadas
     */
    public ResultadoBusqueda<Medico> searchMedicosActivosByNombre(String nombre, double umbralSimilitud) {

        log.debug("Buscando médicos activos por parecido: {}", nombre);

        List<String> palabrasBusqueda = NormalizadorBusqueda.findPalabrasBusqueda(nombre);
        if (palabrasBusqueda.isEmpty()) {
            return ResultadoBusqueda.vacio();
        }

        //Coincidencia por palabras: cada una contenida en el nombre o en el apellido
        Specification<Medico> specificationBusqueda = EspecificacionBusquedaPalabras
                .<Medico>buildSpecificationPalabras(palabrasBusqueda, List.of(Medico_.nombre, Medico_.apellido))
                .and((root, query, cb) -> cb.isNull(root.get(Medico_.deletedAt)));
        List<Medico> medicosEncontrados = medicoRepository.findAll(specificationBusqueda,
                Sort.by(Medico_.APELLIDO, Medico_.NOMBRE));

        //Sin coincidencias exactas por palabra: probar por similitud (errores de tipeo), avisando que es aproximada
        ResultadoBusqueda<Medico> resultadoBusqueda = new ResultadoBusqueda<>(medicosEncontrados, CoincidenciaBusqueda.EXACTA);
        if (medicosEncontrados.isEmpty()) {
            resultadoBusqueda = new ResultadoBusqueda<>(medicoRepository.findActivosBySimilitudNombre(
                    String.join(" ", palabrasBusqueda), umbralSimilitud, LIMITE_SIMILITUD), CoincidenciaBusqueda.APROXIMADA);
        }

        return resultadoBusqueda;

    }

    //endregion

}
