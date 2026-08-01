package com.accesmed.backend.Domain;

/**
 * Catálogo fijo de motivos de cancelación de un {@link Turno}. No es configurable por la
 * clínica en esta versión.
 */
public enum MotivoCancelacion {

    SOLICITUD_DEL_PACIENTE,
    VALIDACION_RECHAZADA,
    VALIDACION_VENCIDA,
    BAJA_DE_MEDICO,
    BAJA_DE_PRESTACION,
    DECISION_ADMINISTRATIVA

}
