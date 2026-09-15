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

}
