package com.accesmed.backend.Application.Ports;

import java.util.UUID;

/**
 * Contrato que el núcleo necesita de {@code Security} para provisionar y desactivar
 * usuarios, sin conocer nada de su implementación interna. Lo define el núcleo (quien lo
 * consume desde {@code MedicoApp}/{@code AdminApp}), lo implementa {@code Security}
 * ({@code GestionUsuarioAdapter}). Si el día de mañana la autenticación se extrae a otro
 * sistema, esta interfaz es el único punto que cambia — el resto del núcleo no se entera.
 */
public interface GestionUsuarioPort {

    /**
     * Crea (o reemplaza, si ya tenía uno activo) el usuario de acceso de un médico.
     *
     * @param medicoId {@code UUID} id del médico
     * @param mail {@code String} mail de login (se copia de {@code Medico.email})
     */
    void asignarUsuarioAMedico(UUID medicoId, String mail);

    /**
     * Crea (o reemplaza, si ya tenía uno activo) el usuario de acceso de un admin.
     *
     * @param adminId {@code UUID} id del admin
     * @param mail {@code String} mail de login (se copia de {@code Admin.email})
     */
    void asignarUsuarioAAdmin(UUID adminId, String mail);

    /**
     * Da de baja el usuario de acceso vinculado a un médico, si existe.
     *
     * @param medicoId {@code UUID} id del médico
     */
    void desactivarUsuarioDeMedico(UUID medicoId);

    /**
     * Da de baja el usuario de acceso vinculado a un admin, si existe.
     *
     * @param adminId {@code UUID} id del admin
     */
    void desactivarUsuarioDeAdmin(UUID adminId);

    /**
     * Da de baja un usuario directamente, sin que implique dar de baja al médico/admin
     * vinculado (a diferencia de {@link #desactivarUsuarioDeMedico}/
     * {@link #desactivarUsuarioDeAdmin}, que sí lo dan de baja porque parten de la baja
     * de la persona). Revoca los refresh tokens vigentes y avisa por mail.
     *
     * @param usuarioId {@code UUID} id del usuario
     * @param motivo {@code String} motivo de la baja, o {@code null}/vacío para un default
     */
    void desactivarUsuarioDirecto(UUID usuarioId, String motivo);

    /**
     * Dispara el mail de restablecimiento de contraseña para un usuario (mismo mecanismo
     * que "olvidé mi contraseña", disparado por el SuperAdmin sobre un tercero o por el
     * propio usuario sobre sí mismo).
     *
     * @param usuarioId {@code UUID} id del usuario
     */
    void dispararResetContrasena(UUID usuarioId);

    /**
     * Inicia el cambio de mail de un usuario: valida el mail nuevo y manda el mail de
     * confirmación a esa casilla. El mail solo se aplica cuando se confirma el token
     * (ver {@code AuthApp#confirmarCambioMail}) — el mail actual sigue vigente hasta
     * entonces.
     *
     * @param usuarioId {@code UUID} id del usuario
     * @param mailNuevo {@code String} mail nuevo propuesto
     */
    void iniciarCambioMail(UUID usuarioId, String mailNuevo);

}
