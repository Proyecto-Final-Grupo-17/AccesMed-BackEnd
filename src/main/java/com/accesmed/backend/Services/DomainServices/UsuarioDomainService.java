package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Usuario;
import com.accesmed.backend.Repositories.UsuarioRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de dominio y persistencia para la entidad {@code Usuario}.
 * Encapsula guardar, buscar, la baja lógica y validaciones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final UsuarioRepository usuarioRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda un usuario en la base de datos.
     *
     * @param usuario {@code Usuario} entidad a persistir
     * @return {@code Usuario} el usuario guardado
     */
    public Usuario saveUsuario(Usuario usuario) {

        log.debug("Guardando usuario: mail={}", usuario.getMail());

        return usuarioRepository.save(usuario);

    }

    /**
     * Busca un usuario activo por su identificador.
     *
     * @param id {@code UUID} identificador del usuario
     * @return {@code Usuario} el usuario activo correspondiente al id
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
     *         usuario activo con ese id
     */
    public Usuario findUsuarioActivoById(UUID id) {

        log.debug("Buscando usuario activo por id: {}", id);

        return usuarioRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("No se encontró el usuario activo: id={}", id);
                    return new RecursoNoEncontradoException(getClass(), "USUARIO_NO_ENCONTRADO",
                            "No se encontró el usuario solicitado.");
                });

    }

    /**
     * Busca un usuario activo por su correo electrónico.
     *
     * @param mail {@code String} correo electrónico del usuario
     * @return {@code Optional<Usuario>} el usuario si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    public Optional<Usuario> findUsuarioActivoByMail(String mail) {

        log.debug("Buscando usuario activo por mail: {}", mail);

        return usuarioRepository.findByMailAndDeletedAtIsNull(mail);

    }

    /**
     * Busca el usuario activo vinculado a un médico.
     *
     * @param medicoId {@code UUID} identificador del médico
     * @return {@code Optional<Usuario>} el usuario vinculado al médico si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    public Optional<Usuario> findUsuarioActivoByMedicoId(UUID medicoId) {

        log.debug("Buscando usuario activo vinculado a médico: medicoId={}", medicoId);

        return usuarioRepository.findByMedicoIdAndDeletedAtIsNull(medicoId);

    }

    /**
     * Busca el usuario activo vinculado a un admin.
     *
     * @param adminId {@code UUID} identificador del admin
     * @return {@code Optional<Usuario>} el usuario vinculado al admin si existe y está activo,
     *         {@code Optional.empty()} en caso contrario
     */
    public Optional<Usuario> findUsuarioActivoByAdminId(UUID adminId) {

        log.debug("Buscando usuario activo vinculado a admin: adminId={}", adminId);

        return usuarioRepository.findByAdminIdAndDeletedAtIsNull(adminId);

    }

    /**
     * Realiza la baja lógica de un usuario.
     *
     * @param usuario {@code Usuario} usuario a dar de baja
     * @param motivo {@code String} motivo de la baja
     */
    public void softDeleteUsuario(Usuario usuario, String motivo) {

        log.debug("Dando de baja usuario: mail={}, motivo={}", usuario.getMail(), motivo);

        usuario.setDeletedAt(Instant.now());
        usuario.setDeletedReason(motivo);

        saveUsuario(usuario);

    }

    //endregion

}
