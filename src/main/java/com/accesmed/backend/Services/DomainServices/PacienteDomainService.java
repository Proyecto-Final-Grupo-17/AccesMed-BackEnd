package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Paciente;
import com.accesmed.backend.Repositories.PacienteRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import com.accesmed.backend.Services.Errors.ValidacionException;
import com.accesmed.backend.Services.Utils.NormalizadorTelefono;
import java.util.List;
import java.util.Optional;

/**
 * Lógica de dominio y persistencia para la entidad {@code Paciente}.
 * Encapsula guardar, buscar, validaciones de unicidad y la baja lógica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacienteDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PacienteRepository pacienteRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un paciente en la base de datos.
     *
     * @param paciente {@code Paciente} entidad a persistir
     * @return {@code Paciente} el paciente guardado
     */
    public Paciente savePaciente(Paciente paciente) {

        log.debug("Guardando paciente: dni={}", paciente.getDni());

        return pacienteRepository.save(paciente);

    }

    /**
     * Busca un paciente activo por su identificador.
     *
     * @param id {@code UUID} identificador del paciente
     * @return {@code Paciente} el paciente activo correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         paciente activo con ese id
     */
    public Paciente findPacienteActivoById(UUID id) {

        log.debug("Buscando paciente activo por id: {}", id);

        return pacienteRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el paciente activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "PACIENTE_NO_ENCONTRADO",
                            "No se encontró el paciente solicitado. Es posible que haya sido dado de baja.");
                });

    }

    /**
     * Valida que el DNI del paciente sea único entre los pacientes activos.
     *
     * @param dni {@code String} DNI a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un paciente
     *         activo con ese DNI
     */
    public void validateDniPacienteIsUnique(String dni) {

        if (pacienteRepository.existsByDniAndDeletedAtIsNull(dni)) {
            log.warn("No se pudo crear el paciente: DNI {} ya existe", dni);
            throw new ReglaNegocioException(getClass(), "PACIENTE_DNI_DUPLICADO",
                    "Ya existe un paciente con el DNI " + dni + ".");
        }

    }

    /**
     * Valida que el DNI del paciente sea único entre los activos, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param dni {@code String} DNI a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro paciente
     *         activo con ese DNI
     */
    public void validateDniPacienteIsUnique(String dni, UUID idExcluido) {

        if (pacienteRepository.existsByDniAndDeletedAtIsNullAndIdNot(dni, idExcluido)) {
            log.warn("No se pudo actualizar el paciente: DNI {} ya existe en otro paciente", dni);
            throw new ReglaNegocioException(getClass(), "PACIENTE_DNI_DUPLICADO",
                    "Ya existe otro paciente con el DNI " + dni + ".");
        }

    }

    /**
     * Valida que el número de teléfono del paciente sea único entre los pacientes activos.
     *
     * @param numeroTelefono {@code String} número de teléfono a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un paciente
     *         activo con ese número de teléfono
     */
    public void validateNumeroTelefonoPacienteIsUnique(String numeroTelefono) {

        if (pacienteRepository.existsByNumeroTelefonoAndDeletedAtIsNull(numeroTelefono)) {
            log.warn("No se pudo crear el paciente: número de teléfono {} ya existe", numeroTelefono);
            throw new ReglaNegocioException(getClass(), "PACIENTE_NUMERO_TELEFONO_DUPLICADO",
                    "Ya existe un paciente con el número de teléfono " + numeroTelefono + ".");
        }

    }

    /**
     * Valida que el número de teléfono del paciente sea único entre los activos,
     * excluyendo un id concreto. Útil para la actualización.
     *
     * @param numeroTelefono {@code String} número de teléfono a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro paciente
     *         activo con ese número de teléfono
     */
    public void validateNumeroTelefonoPacienteIsUnique(String numeroTelefono, UUID idExcluido) {

        if (pacienteRepository.existsByNumeroTelefonoAndDeletedAtIsNullAndIdNot(numeroTelefono, idExcluido)) {
            log.warn("No se pudo actualizar el paciente: número de teléfono {} ya existe en otro paciente", numeroTelefono);
            throw new ReglaNegocioException(getClass(), "PACIENTE_NUMERO_TELEFONO_DUPLICADO",
                    "Ya existe otro paciente con el número de teléfono " + numeroTelefono + ".");
        }

    }

    /**
     * Valida que el email del paciente sea único entre los pacientes activos.
     *
     * @param email {@code String} email a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un paciente
     *         activo con ese email
     */
    public void validateEmailPacienteIsUnique(String email) {

        if (pacienteRepository.existsByEmailAndDeletedAtIsNull(email)) {
            log.warn("No se pudo crear el paciente: email {} ya existe", email);
            throw new ReglaNegocioException(getClass(), "PACIENTE_EMAIL_DUPLICADO",
                    "Ya existe un paciente con el email " + email + ".");
        }

    }

    /**
     * Valida que el email del paciente sea único entre los activos, excluyendo un id
     * concreto. Útil para la actualización.
     *
     * @param email {@code String} email a verificar
     * @param idExcluido {@code UUID} id a excluir de la búsqueda
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe otro paciente
     *         activo con ese email
     */
    public void validateEmailPacienteIsUnique(String email, UUID idExcluido) {

        if (pacienteRepository.existsByEmailAndDeletedAtIsNullAndIdNot(email, idExcluido)) {
            log.warn("No se pudo actualizar el paciente: email {} ya existe en otro paciente", email);
            throw new ReglaNegocioException(getClass(), "PACIENTE_EMAIL_DUPLICADO",
                    "Ya existe otro paciente con el email " + email + ".");
        }

    }

    /**
     * Realiza la baja lógica de un paciente.
     *
     * @param paciente {@code Paciente} paciente a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeletePaciente(Paciente paciente, String motivo) {

        log.debug("Dando de baja paciente: dni={}, motivo={}", paciente.getDni(), motivo);

        paciente.setDeletedAt(Instant.now());
        paciente.setDeletedReason(motivo);
        //deletedBy se completará cuando exista el módulo de seguridad

        savePaciente(paciente);

    }

    /**
     * Busca un paciente activo por su número de teléfono, comparando solo los dígitos (el número
     * guardado puede tener espacios, guiones o {@code +}). Es la base de la identidad del canal chatbot.
     *
     * @param numeroTelefono {@code String} número de teléfono tal como llega del canal (ej. {@code +5492617742682})
     * @return {@code Paciente} el paciente activo dueño de ese teléfono
     * @throws ValidacionException {@code ValidacionException} si el número no es un teléfono válido
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         paciente activo con ese teléfono
     */
    public Paciente findPacienteActivoByNumeroTelefono(String numeroTelefono) {

        String digitos = validateNumeroTelefonoEsValido(numeroTelefono);

        log.debug("Buscando paciente activo por número de teléfono normalizado");

        return pacienteRepository.findActivoByNumeroTelefonoNormalizado(digitos)
                .orElseThrow(() -> {
                    log.warn("No se encontró un paciente activo para el teléfono ingresado por el canal");
                    return new RecursoNoEncontradoException(getClass(), "PACIENTE_NO_ENCONTRADO",
                            "No encontramos un paciente registrado con este número de teléfono.");
                });

    }

    /**
     * Busca un paciente activo por su número de teléfono sin lanzar excepción si no existe: lo
     * usa el canal de Chatwoot para saber si quien escribe ya está registrado (no es un error que
     * no lo esté). Un teléfono inválido se trata como "no registrado".
     *
     * @param numeroTelefono {@code String} número de teléfono tal como llega del canal (ej. {@code +5492617742682})
     * @return {@code Optional<Paciente>} el paciente activo dueño de ese teléfono, o vacío si no hay
     */
    public Optional<Paciente> findOptionalPacienteActivoByNumeroTelefono(String numeroTelefono) {

        if (!NormalizadorTelefono.esValido(numeroTelefono)) {
            log.debug("Teléfono del canal no válido: se considera paciente no registrado");
            return Optional.empty();
        }

        Optional<Paciente> pacienteActivo = pacienteRepository.findActivoByNumeroTelefonoNormalizado(
                NormalizadorTelefono.normalizarDigitos(numeroTelefono));
        return pacienteActivo;

    }

    /**
     * Valida que el número de teléfono del paciente sea único entre los pacientes activos,
     * comparando solo los dígitos (ignora el formato con el que esté guardado cada número).
     *
     * @param numeroTelefono {@code String} número de teléfono a verificar
     * @throws ValidacionException {@code ValidacionException} si el número no es un teléfono válido
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe un paciente
     *         activo con ese número de teléfono
     */
    public void validateNumeroTelefonoPacienteNormalizadoIsUnique(String numeroTelefono) {

        String digitos = validateNumeroTelefonoEsValido(numeroTelefono);

        if (pacienteRepository.findActivoByNumeroTelefonoNormalizado(digitos).isPresent()) {
            log.warn("No se pudo crear el paciente: ya existe uno con el teléfono ingresado por el canal");
            throw new ReglaNegocioException(getClass(), "PACIENTE_NUMERO_TELEFONO_DUPLICADO",
                    "Ya existe un paciente registrado con este número de teléfono.");
        }

    }

    /**
     * Valida que el texto sea un teléfono utilizable y devuelve sus dígitos.
     *
     * @param numeroTelefono {@code String} número de teléfono a validar
     * @return {@code String} el número normalizado a solo dígitos
     * @throws ValidacionException {@code ValidacionException} si el número es nulo o no tiene
     *         una cantidad de dígitos razonable
     */
    private String validateNumeroTelefonoEsValido(String numeroTelefono) {

        if (!NormalizadorTelefono.esValido(numeroTelefono)) {
            log.warn("El teléfono recibido del canal no es válido");
            throw new ValidacionException(getClass(),
                    List.of("No se pudo identificar el número de teléfono del paciente."));
        }

        return NormalizadorTelefono.normalizarDigitos(numeroTelefono);

    }

    //endregion

}
