package com.accesmed.backend.Domain;

/**
 * Catálogo fijo de permisos del sistema, definido en código. Convención de nombre:
 * {@code <MODULO>_<ACCION>}, un valor por cada caso de uso interno.
 *
 * <p>Se relaciona con {@link Rol} como asociación N:N, materializada en la tabla
 * {@code rol_permiso} vía {@code @ElementCollection} (ver {@link Rol#getPermisos()}).</p>
 */
public enum Permiso {

    // CONFIG
    CONFIG_MODIFICAR,

    // MED
    MED_ALTA,
    MED_MODIFICAR,
    MED_BAJA,
    MED_CONSULTAR,
    MED_ASIGNAR_PRESTACION,

    // PREST
    PREST_ALTA,
    PREST_MODIFICAR,
    PREST_BAJA,
    PREST_CONSULTAR,

    // AGEN
    AGEN_CONFIGURAR,
    AGEN_CONSULTAR,

    // PACIENTE
    PACIENTE_ALTA,
    PACIENTE_MODIFICAR,
    PACIENTE_BAJA,
    PACIENTE_CONSULTAR,

    // OS
    OS_ALTA,
    OS_MODIFICAR,
    OS_BAJA,
    OS_CONSULTAR,

    // TURN
    TURN_REGISTRAR,
    TURN_VALIDAR,
    TURN_CONFIRMAR,
    TURN_REPROGRAMAR,
    TURN_CANCELAR,
    TURN_ANUNCIAR,
    TURN_INICIAR,
    TURN_FINALIZAR,
    TURN_CONSULTAR,

    // USER / AUTZ
    USER_ALTA,
    USER_MODIFICAR,
    USER_BAJA,
    USER_CONSULTAR,
    AUTZ_ROL_ALTA,
    AUTZ_ROL_MODIFICAR,
    AUTZ_ROL_BAJA,
    AUTZ_ROL_ASIGNAR

}
